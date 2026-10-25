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
        // Empty open menus first: once our click listener is gone their items must not be takeable.
        if (menuManager != null) menuManager.closeAll();

        // Centralized player cleanup + async skill save
        if (playerSessionManager != null) playerSessionManager.shutdown();
        if (classService != null) classService.shutdown(); // writes every loaded class profile now

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
    }

    private Attributes.AttributesConfig attributesConfig;
    private Attributes.AttributeService attributeService;
    private Manager.AttributeEffects attributeEffects;
    private Manager.SecondaryEffects secondaryEffects;
    private Manager.PvpTag pvpTag;

    /** Attack/draw/movement speed and spell cooldown from the attribute engine. */
    public Manager.SecondaryEffects getSecondaryEffects() { return secondaryEffects; }
    public Manager.PvpTag getPvpTag() { return pvpTag; }
    private Attributes.BuffSource attributeBuffs;
    private Attributes.PlayerRealms playerRealms;

    /** Each player's realm (default until Ascension exists; admins can set a temporary test realm). */
    public Attributes.PlayerRealms getPlayerRealms() { return playerRealms; }

    /** Temporary attribute buffs (admin dev buffs); in memory only. */
    public Attributes.BuffSource getAttributeBuffs() { return attributeBuffs; }

    /** Vitality / Fortitude → max health and defense (see Manager.AttributeEffects). */
    public Manager.AttributeEffects getAttributeEffects() { return attributeEffects; }

    public Attributes.AttributeService getAttributeService() { return attributeService; }

    public Attributes.AttributesConfig getAttributesConfig() { return attributesConfig; }

    private Classes.ClassService classService;
    private Classes.AbilityCaster abilityCaster;

    /** Classes and abilities (docs/design/CLASSES.md): profiles, loadouts, class XP. */
    public Classes.ClassService getClassService() { return classService; }
    public Classes.AbilityCaster getAbilityCaster() { return abilityCaster; }

    /**
     * classes.yml + abilities.yml (written on first install). Null when a file can't be read or
     * parsed: the caller then keeps what it had, so a typo never touches anyone's classes.
     */
    private Classes.ClassCatalog loadClassCatalog() {
        File classesFile = new File(getDataFolder(), "classes.yml");
        File abilitiesFile = new File(getDataFolder(), "abilities.yml");
        if (!classesFile.exists()) saveResource("classes.yml", false);
        if (!abilitiesFile.exists()) saveResource("abilities.yml", false);
        YamlConfiguration classesYml = new YamlConfiguration();
        YamlConfiguration abilitiesYml = new YamlConfiguration();
        try {
            classesYml.load(classesFile);
            abilitiesYml.load(abilitiesFile);
        } catch (Exception e) {
            getLogger().severe("[Classes] classes.yml / abilities.yml could not be read (" + e.getMessage()
                    + "). Nothing was changed; fix the file and run /cerb reload.");
            return null;
        }
        Classes.ClassCatalog catalog = Classes.ClassCatalog.from(classesYml, abilitiesYml);
        for (String problem : catalog.problems()) getLogger().warning("[Classes] " + problem);
        getLogger().info("[Classes] " + catalog.classes().size() + " classes and " + catalog.abilities().size() + " abilities loaded");
        return catalog;
    }

    /** combat.yml's crit / lifesteal curves for the attribute engine (defaults until it is loaded). */
    private Attributes.AttributeRules.Curves attributeCurves() {
        if (combatConfig == null) return Attributes.AttributeRules.DEFAULT_CURVES;
        return new Attributes.AttributeRules.Curves(combatConfig.critChanceScaling, combatConfig.critMultiplierScaling,
                Attributes.AttributeRules.DEFAULT_CURVES.lifestealSkill(), combatConfig.wmLifestealScaling);
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
        // After combat.yml: the attribute engine reuses its crit and lifesteal curves.
        attributesConfig = new Attributes.AttributesConfig(this, this::attributeCurves);
        attributeService = new Attributes.AttributeService(attributesConfig::rules);
        attributeService.addSource(new Attributes.SkillSource(skillManager));
        attributeBuffs = new Attributes.BuffSource();
        attributeService.addSource(attributeBuffs);
        playerRealms = new Attributes.PlayerRealms(attributesConfig::defaultRealm);
        attributeService.setRealms(id -> {
            Manager.Realm r = playerRealms.realmOf(id);
            return new Attributes.RealmContext(r, combatConfig.mobHealthScaling.realmMultiplier(r.tier()));
        });
        playerVirtualHealthManager.setRegenScale(id -> attributeService.get(id).realmMultiplier());
        defenseBarManager.setRegenScale(id -> attributeService.get(id).realmMultiplier());
        playerVirtualHealthManager.setReductionCap(() -> attributeService.rules().limits().totalMitigation());
        playerVirtualHealthManager.setPvpUsesAttributes(() -> attributeService.rules().switches().pvpDefense());
        playerDefenseManager.setAttributeService(attributeService);
        attributeEffects = new Manager.AttributeEffects(this, attributeService, playerVirtualHealthManager, playerDefenseManager,
                playerManaManager);
        // Speed and handling stats (attack/draw/movement speed, spell cooldown), paused during PvP by default
        secondaryEffects = new Manager.SecondaryEffects(this, attributeService);
        pvpTag = new Manager.PvpTag(this, () -> attributeService.rules().secondary().pvpTagSeconds(), secondaryEffects::apply);
        secondaryEffects.setPvpTag(pvpTag);
        secondaryEffects.setDebug(this::isCombatDebugEnabled);
        attributeEffects.setSecondaryEffects(secondaryEffects);
        // Classes and abilities (docs/design/CLASSES.md): one more attribute source (equipped passives,
        // running buffs), loaded per player on join. A broken catalog leaves classes off, touching nothing.
        Classes.ClassCatalog classCatalog = loadClassCatalog();
        classService = new Classes.ClassService(this, databaseManager, skillManager, attributeService,
                () -> pvpTag, classCatalog != null ? classCatalog : Classes.ClassCatalog.empty());
        classService.setAttributesChanged(p -> attributeEffects.refresh(p));
        attributeService.addSource(classService);
        skillManager.setLevelListener(new SkillManager.LevelListener() {
            @Override public void loaded(java.util.UUID p) { attributeService.track(p); attributeEffects.refreshLater(p); classService.skillsChanged(p); }
            @Override public void changed(java.util.UUID p) { attributeService.markDirty(p); attributeEffects.refreshLater(p); classService.skillsChanged(p); }
            @Override public void unloaded(java.util.UUID p) { attributeService.untrack(p); attributeBuffs.clear(p); playerRealms.clearTestRealm(p); }
        });
        sessionLogger = new SessionLogger(this);

        // Skill catalog (names, descriptions, training, effects; skills.yml overrides)
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
        playerMenuGUI = new PlayerMenuGUI(this); // Player Menu: hotbar head, /menu, hub destinations
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
        Objects.requireNonNull(getCommand("stats")).setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player p) menuManager.openRoot(p, new GUIs.stats.StatsMenu(this));
            else sender.sendMessage("Only players have stats.");
            return true;
        });
        Objects.requireNonNull(getCommand("menu")).setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player p) playerMenuGUI.open(p);
            else sender.sendMessage("Only players have a Player Menu.");
            return true;
        });
        var cerbCmd = Objects.requireNonNull(getCommand("cerb"));
        cerbCmd.setExecutor(new CerbCommand(this));
        cerbCmd.setTabCompleter(new Commands.CerbTabCompleter(this));
        classCommand = new Commands.ClassCommand(this);
        var classCmd = Objects.requireNonNull(getCommand("class"));
        classCmd.setExecutor(classCommand);
        classCmd.setTabCompleter(classCommand);
    }

    private Commands.ClassCommand classCommand;

    /** /class, and the admin /cerb class subcommand. */
    public Commands.ClassCommand getClassCommand() { return classCommand; }

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
                playerMenuGUI, adminChestGUI, mobRegistry,
                chunkDiscoveryManager
        );
        playerSessionManager = new PlayerSessionManager(this, services);
        pm.registerEvents(playerSessionManager, this);

        // ── Combat & mobs ──
        pm.registerEvents(new CombatListener(this, skillManager, spellManager, mobRegistry), this);
        pm.registerEvents(new MobKillListener(this, skillManager, skillMapper, mobRegistry), this);
        this.mobScalingListener = new MobScalingListener(this);
        pm.registerEvents(mobScalingListener, this);
        pm.registerEvents(mobLabelManager, this);

        // ── HUD & defense (reactive — damage/heal/consume only) ──
        pm.registerEvents(new PlayerHUDListener(playerHUDManager, playerDefenseManager), this);
        pm.registerEvents(new DefenseUpdateListener(playerHUDManager), this);
        pm.registerEvents(new CustomArmorListener(playerDefenseManager, playerHUDManager, this), this);

        // ── Classes: cast keys and combat hooks. Registered BEFORE the damage pipeline: at the same
        // priority, listeners run in registration order, and a spear stab replaced by a cast must
        // be cancelled before the pipeline drains the mob's HP pool.
        abilityCaster = new Classes.AbilityCaster(this, classService, playerManaManager, () -> spellManager, () -> virtualHealthListener);
        abilityCaster.setCanAct(p -> Classes.AbilityListener.noContainerOpen(p)
                && !playerVirtualHealthManager.isRespawning(p) && !playerVirtualHealthManager.hasPendingCustomDeath(p));
        abilityCaster.setSpellCooldown((id, ms) -> secondaryEffects.cooldownMs(id, ms));
        Classes.CastFeedback castFeedback = new Classes.CastFeedback(playerHUDManager);
        abilityCaster.setFeedback(castFeedback);
        Classes.AbilityBar abilityBar = new Classes.AbilityBar(this, classService, playerManaManager);
        pm.registerEvents(new Classes.AbilityListener(this, classService, abilityCaster, castFeedback, abilityBar), this);

        // ── Virtual health (damage interception, regen block) ──
        this.virtualHealthListener = new VirtualHealthListener(playerVirtualHealthManager,
                new FirstAidSkill("First Aid", playerVirtualHealthManager, craftingManager), this);
        virtualHealthListener.setCombatSkillProcessor(combatSkillProcessor);
        Manager.PveOffense pveOffense = new Manager.PveOffense(attributeService);
        pveOffense.setForcedCrit(classService::consumeEmpower); // Feint: the next melee hit crits
        virtualHealthListener.setPveOffense(pveOffense);
        virtualHealthListener.setCounterHook(abilityCaster::tryCounter); // Riposte
        spellManager.setVirtualHealthListener(virtualHealthListener);
        spellManager.setSkillManager(skillManager);
        pm.registerEvents(virtualHealthListener, this);

        // ── Other ──
        pm.registerEvents(new CustomItemListener(spellManager, this), this);
        pm.registerEvents(secondaryEffects, this);
        pm.registerEvents(pvpTag, this);
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
        if (attributeEffects != null) {
            attributeEffects.refreshAll(); // new weights/limits: re-apply max health and defense
        }
        if (classService != null) {
            Classes.ClassCatalog next = loadClassCatalog();
            if (next != null) classService.setCatalog(next); // a file that doesn't parse keeps the old catalog
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

    /**
     * /cerb mana <player> [amount|full|empty]: show or set a player's Essence (an admin and test
     * tool: playtests need "too little Essence" on demand). Clamped to 0..max by the mana manager.
     */
    public void adjustMana(CommandSender sender, String[] args) {
        String usage = ChatColor.RED + "Use: /cerb mana <player> [amount|full|empty]";
        if (args.length < 2 || args.length > 3) { sender.sendMessage(usage); return; }
        Player target = getServer().getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(ChatColor.RED + "Player not online: " + args[1]); return; }
        Double set = null;
        if (args.length == 3) {
            String v = args[2].toLowerCase(java.util.Locale.ROOT);
            if (v.equals("full") || v.equals("max")) set = Double.POSITIVE_INFINITY;
            else if (v.equals("empty")) set = 0.0;
            else {
                try { set = Double.parseDouble(v); } catch (NumberFormatException e) { sender.sendMessage(usage); return; }
                if (!Double.isFinite(set) || set < 0) { sender.sendMessage(ChatColor.RED + "Amount must be a number >= 0."); return; }
            }
        }
        Double amount = set;
        // Folia: mana belongs to the target's region thread.
        target.getScheduler().run(this, task -> {
            if (amount != null) playerManaManager.setPlayerCurrentMana(target, Math.min(amount, playerManaManager.getMaxMana(target)));
            if (playerHUDManager != null) playerHUDManager.updateHUD(target);
            sender.sendMessage(ChatColor.GREEN + target.getName() + "'s Essence: "
                    + Math.round(playerManaManager.getCurrentMana(target)) + " / " + Math.round(playerManaManager.getMaxMana(target)));
        }, null);
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
            if (!skillManager.hasLoadedSkills(target.getUniqueId())) {
                sender.sendMessage(ChatColor.RED + target.getName() + "'s skills are not loaded yet - nothing changed. Try again in a moment.");
                return;
            }
            if (mode.equals("level")) {
                skillManager.setSkillLevel(target, skill, (int) value);
            } else {
                skillManager.addXpAndCheckLevelUp(skill, value, target);
            }
            int lvl = skillManager.getSkillLevel(target, skill);
            int xp = skillManager.getSkillXP(target, skill);
            String shown = SkillNames.display(skill); // "Skills." would name the class Skills.Skills here
            sender.sendMessage(ChatColor.GREEN + target.getName() + ": " + (shown.equals(skill) ? skill : shown + " (" + skill + ")")
                    + " is now level " + lvl + " (" + xp + " XP)");
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
    /** Runtime counters for checking load (e.g. that browsing menus does no database work). */
    public void showQueues(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "Cerberus runtime counters:");
        sender.sendMessage(ChatColor.GRAY + "Database connections since start: " + ChatColor.WHITE + databaseManager.connectionsOpened());
        sender.sendMessage(ChatColor.GRAY + "Attribute rebuilds since start: " + ChatColor.WHITE + attributeService.rebuildCount());
        sender.sendMessage(ChatColor.GRAY + "Attribute previews since start: " + ChatColor.WHITE + attributeService.previewCount());
        sender.sendMessage(ChatColor.GRAY + "Menu animations running: " + ChatColor.WHITE + menuManager.animator().runningCount()
                + ChatColor.GRAY + " (started since start: " + ChatColor.WHITE + menuManager.animator().startedCount() + ChatColor.GRAY + ")");
        sender.sendMessage(ChatColor.GRAY + "Players with a menu open: " + ChatColor.WHITE + menuManager.activeViewers());
        sender.sendMessage(ChatColor.GRAY + "Mob HP pools tracked: " + ChatColor.WHITE + mobVirtualHealthManager.size());
        if (classService != null && abilityCaster != null) {
            sender.sendMessage(ChatColor.GRAY + "Class profiles loaded: " + ChatColor.WHITE + classService.loadedCount()
                    + ChatColor.GRAY + " (casts since start: " + ChatColor.WHITE + abilityCaster.castCount()
                    + ChatColor.GRAY + ", counters: " + ChatColor.WHITE + abilityCaster.counterCount() + ChatColor.GRAY + ")");
        }
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
                if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) adminChestGUI.open(player); // once, not again for the off hand
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
            event.setCancelled(true); // never placed as a block, never equipped
            // The client follows a main-hand right-click with an off-hand one: open once.
            if (event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) playerMenuGUI.open(player);
            return true;
        }
        return false;
    }

    /**
     * A powered shelf swaps its items with the end of the hotbar (vanilla 1.21.9+, measured: one
     * shelf swaps slots 7-9, two chained shelves 4-9, three all nine). The Player Menu head (slot 9)
     * and an operator's admin chest must never go onto a shelf, but the shelf should still work for
     * everything else (owner decision B). So they are lifted out of the hotbar just before the swap
     * and put back on the player's next tick; whatever shelf item landed in their slot goes back to
     * the shelf slot it came from (or to the player, never lost). Meanwhile they exist only
     * server-side, so nobody can take them off a shelf or duplicate them.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPoweredShelf(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (!(e.getClickedBlock().getBlockData() instanceof org.bukkit.block.data.type.Shelf shelf) || !shelf.isPowered()) return;
        Player player = e.getPlayer();
        if (shelfSwaps.containsKey(player.getUniqueId())) return;          // already lifted this tick (off-hand event)
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        if (isProtectedMenuItem(inv.getItemInMainHand())) return;           // using the head itself: its own handler opens the menu
        Map<Integer, ItemStack> lifted = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            ItemStack it = inv.getItem(i);
            if (isProtectedMenuItem(it)) { lifted.put(i, it.clone()); inv.setItem(i, null); }
        }
        if (lifted.isEmpty()) return;
        List<org.bukkit.block.Block> row = shelfRow(e.getClickedBlock(), shelf.getFacing());
        Map<org.bukkit.block.Block, ItemStack[]> before = new HashMap<>();
        for (org.bukkit.block.Block b : row) {
            if (b.getState() instanceof org.bukkit.block.Shelf st) before.put(b, cloneAll(st.getInventory().getContents()));
        }
        shelfSwaps.put(player.getUniqueId(), Boolean.TRUE);
        player.getScheduler().run(this, t -> {
            shelfSwaps.remove(player.getUniqueId());
            for (Map.Entry<Integer, ItemStack> l : lifted.entrySet()) {
                ItemStack landed = inv.getItem(l.getKey());
                inv.setItem(l.getKey(), l.getValue());
                if (landed != null && !landed.getType().isAir() && !returnToShelf(landed, before)) {
                    for (ItemStack left : inv.addItem(landed).values()) player.getWorld().dropItem(player.getLocation(), left);
                }
            }
        }, () -> shelfSwaps.remove(player.getUniqueId()));
    }

    /** Players whose menu items are lifted out for a powered-shelf swap right now. */
    private final Map<UUID, Boolean> shelfSwaps = new ConcurrentHashMap<>();

    /** The clicked shelf and up to two shelves on each side in its row (a chain is at most three). */
    private static List<org.bukkit.block.Block> shelfRow(org.bukkit.block.Block clicked, org.bukkit.block.BlockFace facing) {
        int dx = facing == org.bukkit.block.BlockFace.NORTH || facing == org.bukkit.block.BlockFace.SOUTH ? 1 : 0;
        int dz = 1 - dx;
        List<org.bukkit.block.Block> row = new ArrayList<>();
        for (int k = -2; k <= 2; k++) {
            org.bukkit.block.Block b = clicked.getRelative(k * dx, 0, k * dz);
            if (b.getBlockData() instanceof org.bukkit.block.data.type.Shelf) row.add(b);
        }
        return row;
    }

    /** Put a shelf item that landed in a menu item's slot back into the shelf slot it left (now empty). */
    private static boolean returnToShelf(ItemStack landed, Map<org.bukkit.block.Block, ItemStack[]> before) {
        for (Map.Entry<org.bukkit.block.Block, ItemStack[]> en : before.entrySet()) {
            if (!Bukkit.isOwnedByCurrentRegion(en.getKey().getLocation())) continue;
            if (!(en.getKey().getState() instanceof org.bukkit.block.Shelf st)) continue;
            org.bukkit.inventory.Inventory si = st.getInventory();
            ItemStack[] was = en.getValue();
            for (int s = 0; s < Math.min(was.length, si.getSize()); s++) {
                ItemStack now = si.getItem(s);
                if ((now == null || now.getType().isAir()) && was[s] != null && was[s].isSimilar(landed)
                        && was[s].getAmount() == landed.getAmount()) {
                    si.setItem(s, landed);
                    return true;
                }
            }
        }
        return false;
    }

    private static ItemStack[] cloneAll(ItemStack[] items) {
        ItemStack[] out = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) out[i] = items[i] == null ? null : items[i].clone();
        return out;
    }

    /** Middle-click pick-block must not move the head (or the admin chest) in or out of its slot. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickItem(io.papermc.paper.event.player.PlayerPickItemEvent e) {
        org.bukkit.inventory.PlayerInventory inv = e.getPlayer().getInventory();
        int src = e.getSourceSlot(), dst = e.getTargetSlot();
        if ((src >= 0 && src < inv.getSize() && isProtectedMenuItem(inv.getItem(src)))
                || (dst >= 0 && dst < inv.getSize() && isProtectedMenuItem(inv.getItem(dst)))) {
            e.setCancelled(true);
        }
    }

    /** Right-clicking a mob, item frame, villager... with the head opens the menu instead of giving the head away. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteractEntityWithMenuItem(PlayerInteractEntityEvent e) {
        ItemStack hand = e.getPlayer().getInventory().getItemInMainHand();
        if (!isProtectedMenuItem(hand)) return;
        e.setCancelled(true);
        if (e.getHand() == org.bukkit.inventory.EquipmentSlot.HAND && playerMenuGUI.isPlayerMenuHead(hand)) {
            playerMenuGUI.open(e.getPlayer());
        }
    }

    /** Armor stands have their own event: the head must never end up on one. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onArmorStandWithMenuItem(PlayerArmorStandManipulateEvent e) {
        if (isProtectedMenuItem(e.getPlayerItem()) || isProtectedMenuItem(e.getPlayer().getInventory().getItemInMainHand())) {
            e.setCancelled(true);
        }
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

    /** Which skill a weapon/item trains (skills.yml). */
    public ConfigSkillMapper getSkillMapper() { return skillMapper; }
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

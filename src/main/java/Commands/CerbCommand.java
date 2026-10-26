package Commands;

import Manager.CombatConfig;
import Manager.HealthStateSnapshot;
import Manager.ScalingFormula;
import Manager.SessionLogger;
import Skills.SkillManager;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Map;

public class CerbCommand implements CommandExecutor {

    private static final java.util.List<String> CONSOLE_SAFE =
            java.util.List.of("reload", "status", "backup", "skill", "heal", "log", "class");

    private static final String USAGE =
            ChatColor.YELLOW + "Usage: /cerb <panel|reload|backup|debug|combatdebug|scalingpreview|log|healthstate|status|heal|mana|skill|class|resetdata|"
                    + "worldgen|repairchunk|quest|event|"
                    + "timings|profiler|listentities|showqueues|speed|spawn|mobs|labels|gather>";
    private final CerberusPlugin plugin;

    public CerbCommand(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {
        // Players need cerb.admin. The server console may run the subcommands that never touch
        // the sender's own entity and hand player work to that player's scheduler (Folia).
        boolean console = sender instanceof org.bukkit.command.ConsoleCommandSender;
        String first = args.length > 0 ? args[0].toLowerCase() : "";
        if (console) {
            if (!CONSOLE_SAFE.contains(first)) {
                sender.sendMessage(ChatColor.RED + "From the console use: " + String.join(", ", CONSOLE_SAFE) + ". Other /cerb commands are in-game only.");
                return true;
            }
        } else if (!(sender instanceof Player) || !sender.hasPermission("cerb.admin")) {
            sender.sendMessage(ChatColor.RED + "You lack permission.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(USAGE);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            // 1. Server & Configuration
            case "reload":
                // <<< NEW: reload everything
                plugin.reloadAll();
                sender.sendMessage(ChatColor.GREEN + "Reload complete.");
                break;

            case "backup":
                // <<< NEW: trigger async backup of all player data
                plugin.saveAllPlayers();
                sender.sendMessage(ChatColor.GREEN + "Backup started.");
                break;

            case "debug":
                // <<< NEW: toggle debug
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Use: /cerb debug <on|off>");
                } else {
                    plugin.toggleDebug(args[1].equalsIgnoreCase("on"));
                    sender.sendMessage(ChatColor.AQUA + "Debug: " + args[1]);
                }
                break;

            case "combatdebug":
                // Toggle combat debug mode — shows full pipeline trace on each hit
                if (sender instanceof Player p) {
                    boolean enabled = plugin.toggleCombatDebug(p.getUniqueId());
                    p.sendMessage(ChatColor.AQUA + "Combat debug: " + (enabled ? ChatColor.GREEN + "ON" : ChatColor.RED + "OFF"));
                    if (enabled) {
                        p.sendMessage(ChatColor.GRAY + "You will see pipeline traces for hits you deal or receive.");
                    }
                }
                break;

            case "scalingpreview":
                // Show scaling curve preview for skill stats
                handleScalingPreview(sender, args);
                break;

            case "log":
                // Dump session log for a player
                handleLogDump(sender, args);
                break;

            case "healthstate":
                handleHealthState(sender, args);
                break;

            case "status":
                // <<< NEW: print runtime stats
                plugin.printStatus(sender);
                break;


            // 2. Player Management
            case "heal":
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.RED + "Use: /cerb heal <player>");
                } else {
                    plugin.healPlayer(sender, args[1]);        // <<< FIXED: pass sender + name
                }
                break;

            case "mana":
                plugin.adjustMana(sender, args);               // <<< FIXED
                break;

            case "skill":
                plugin.adjustSkill(sender, args);              // <<< FIXED
                break;

            case "class":
                plugin.getClassCommand().admin(sender, args);  // /cerb class <player> <class> rank <n> | info
                break;

            case "resetdata":
                plugin.resetPlayerData(sender, args);          // <<< FIXED
                break;

            // 3. World Control
            case "worldgen":
                // <<< NEW: toggle chunk‐populate hooks
                plugin.toggleWorldGen(sender, args);
                break;

            case "repairchunk":
                // <<< NEW: run nature auto‑repair in given chunk
                plugin.repairChunk(sender, args);
                break;


            // 4. Event & Quest Control
            case "quest":
                // <<< NEW: assign region objectives
                plugin.giveQuest(sender, args);
                break;

            case "event":
                // <<< NEW: start/stop dynamic event
                plugin.controlEvent(sender, args);
                break;

            // 5. Debug & Diagnostics
            case "timings":
                // <<< NEW: start/stop Bukkit timings
                plugin.handleTimings(sender, args);
                break;

            case "profiler":
                // <<< NEW: trigger Spark snapshot
                plugin.triggerProfiler(sender, args);
                break;

            case "listentities":
                // <<< NEW: show nearby entity counts
                plugin.listEntitiesAround(sender, args);
                break;

            case "showqueues":
                // <<< NEW: show scheduled tasks & HUD timers
                plugin.showQueues(sender);
                break;

            case "mobs":
                if (!(sender instanceof Player mp)) { sender.sendMessage(ChatColor.RED + "Run /cerb mobs in-game."); break; }
                handleMobsDiag(mp, args);
                break;

            case "labels":
                if (!(sender instanceof Player lp)) { sender.sendMessage(ChatColor.RED + "Run /cerb labels in-game."); break; }
                // /cerb labels [radius] [clean]: our floating labels nearby, and the ones riding no mob (clean removes those)
                double lr = 32;
                boolean lclean = false;
                for (int i = 1; i < args.length; i++) {
                    if (args[i].equalsIgnoreCase("clean")) lclean = true;
                    else try { lr = Math.max(1, Math.min(96, Double.parseDouble(args[i]))); } catch (NumberFormatException ignored) { }
                }
                if (plugin.getMobLabelManager() == null) { lp.sendMessage(ChatColor.RED + "Labels are off."); break; }
                for (String line : plugin.getMobLabelManager().census(lp, lr, 8, lclean)) lp.sendMessage(ChatColor.GRAY + "[Labels] " + line);
                break;

            case "speed":
                if (!(sender instanceof Player sp)) { sender.sendMessage(ChatColor.RED + "Run /cerb speed in-game."); break; }
                handleSpeedDiag(sp, args);
                break;

            case "panel":
            case "admin":
                if (!(sender instanceof Player pp)) { sender.sendMessage(ChatColor.RED + "Run /cerb panel in-game."); break; }
                plugin.getAdminPanel().open(pp);
                break;

            case "spawn":
                if (!(sender instanceof Player sw)) { sender.sendMessage(ChatColor.RED + "Run /cerb spawn in-game (it spawns at your position)."); break; }
                handleSpawn(sw, args);
                break;

            case "gather":
                if (!(sender instanceof Player gp)) { sender.sendMessage(ChatColor.RED + "Run /cerb gather in-game."); break; }
                new GatherCommand(plugin).run(gp, args);
                break;

            default:
                sender.sendMessage(ChatColor.RED + "Unknown sub‑command. " + USAGE);
        }

        return true;
    }

    /**
     * Handle /cerb healthstate [player]
     * Prints the authoritative custom-health snapshot plus session state.
     */
    private void handleHealthState(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
        } else if (sender instanceof Player self) {
            target = self;
        } else {
            sender.sendMessage(ChatColor.RED + "Use: /cerb healthstate <player>");
            return;
        }

        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return;
        }

        HealthStateSnapshot snapshot = plugin.getPlayerVirtualHealthManager().snapshot(target);
        String sessionState = "NONE";
        if (plugin.getPlayerSessionManager() != null && plugin.getPlayerSessionManager().getSession(target.getUniqueId()) != null) {
            sessionState = plugin.getPlayerSessionManager().getSession(target.getUniqueId()).getState().name();
        }

        sender.sendMessage(ChatColor.GOLD + "=== Health State: " + target.getName() + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Custom HP: " + ChatColor.WHITE
                + String.format("%.2f/%.2f", snapshot.currentHp(), snapshot.maxHp()));
        sender.sendMessage(ChatColor.YELLOW + "Pending death: " + ChatColor.WHITE + snapshot.pendingCustomDeath());
        sender.sendMessage(ChatColor.YELLOW + "Lifecycle: " + ChatColor.WHITE + snapshot.lifecycleState());
        sender.sendMessage(ChatColor.YELLOW + "Last damage at: " + ChatColor.WHITE + snapshot.lastDamageAt());
        sender.sendMessage(ChatColor.YELLOW + "Invulnerable until: " + ChatColor.WHITE + snapshot.invulnerableUntil());
        sender.sendMessage(ChatColor.YELLOW + "Vanilla HP: " + ChatColor.WHITE
                + String.format("%.2f", Math.max(0.0, target.getHealth())));
        sender.sendMessage(ChatColor.YELLOW + "Session: " + ChatColor.WHITE + sessionState);
    }

    /**
     * Handle /cerb scalingpreview [all | skill stat]
     * Shows scaling curve values at standard levels.
     */
    /**
     * Handle /cerb log <player>
     * Dumps the player's current ring buffer to a .toon file and prints the path.
     */
    private void handleLogDump(CommandSender sender, String[] args) {
        SessionLogger logger = plugin.getSessionLogger();
        if (logger == null) {
            sender.sendMessage(ChatColor.RED + "SessionLogger not initialized.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Use: /cerb log <player>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
            return;
        }
        String path = logger.dumpLive(target.getUniqueId());
        if (path == null) {
            sender.sendMessage(ChatColor.YELLOW + "No log entries for " + target.getName() + " yet.");
        } else {
            sender.sendMessage(ChatColor.GREEN + "Log dumped: " + ChatColor.WHITE + path);
        }
    }

    /**
     * /cerb speed [player] — dump movement speed diagnostics.
     * Shows base value, final value, every modifier, and relevant skill levels.
     */
    /**
     * /cerb mobs [radius]: the living mobs near you, nearest first, with their health pool and active
     * effects (an admin diagnostic; also how playtests read a stun or a slow on a mob, which the
     * client is never told). Read-only. The player's own region thread runs it, and only mobs that
     * thread owns are listed.
     */
    private void handleMobsDiag(Player sender, String[] args) {
        double r = 12;
        if (args.length >= 2) {
            try { r = Math.max(1, Math.min(48, Double.parseDouble(args[1]))); } catch (NumberFormatException e) { sender.sendMessage(ChatColor.RED + "Use: /cerb mobs [radius 1-48]"); return; }
        }
        Manager.MobVirtualHealthManager pools = plugin.getMobVirtualHealthManager();
        java.util.List<org.bukkit.entity.LivingEntity> mobs = new java.util.ArrayList<>();
        for (org.bukkit.entity.Entity e : sender.getNearbyEntities(r, r, r)) {
            if (e instanceof org.bukkit.entity.LivingEntity le && !(e instanceof Player) && Bukkit.isOwnedByCurrentRegion(e)) mobs.add(le);
        }
        mobs.sort(java.util.Comparator.comparingDouble(le -> le.getLocation().distanceSquared(sender.getLocation())));
        sender.sendMessage(ChatColor.GOLD + "Mobs within " + Math.round(r) + " blocks: " + mobs.size());
        for (int i = 0; i < mobs.size() && i < 12; i++) {
            org.bukkit.entity.LivingEntity le = mobs.get(i);
            StringBuilder line = new StringBuilder();
            line.append(ChatColor.YELLOW).append(le.getType().getKey().getKey()).append(ChatColor.GRAY)
                    .append(String.format(java.util.Locale.ROOT, " %.1fm", le.getLocation().distance(sender.getLocation())));
            if (pools != null && pools.has(le)) {
                line.append(String.format(java.util.Locale.ROOT, " hp %.1f/%.1f", pools.getCurrent(le), pools.getMax(le)));
            }
            java.util.List<String> fx = new java.util.ArrayList<>();
            for (org.bukkit.potion.PotionEffect pe : le.getActivePotionEffects()) {
                fx.add(pe.getType().getKey().getKey() + " " + (pe.getAmplifier() + 1) + " " + String.format(java.util.Locale.ROOT, "%.1fs", pe.getDuration() / 20.0));
            }
            line.append(ChatColor.WHITE).append(fx.isEmpty() ? " no effects" : " effects: " + String.join(", ", fx));
            sender.sendMessage(line.toString());
        }
    }

    private void handleSpeedDiag(Player sender, String[] args) {
        Player target = sender;
        if (args.length >= 2) {
            Player found = Bukkit.getPlayer(args[1]);
            if (found == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[1]);
                return;
            }
            target = found;
        }

        AttributeInstance attr = target.getAttribute(Attribute.MOVEMENT_SPEED);
        if (attr == null) {
            sender.sendMessage(ChatColor.RED + "No movement speed attribute.");
            return;
        }

        sender.sendMessage(ChatColor.GOLD + "=== Speed Diagnostics: " + target.getName() + " ===");
        sender.sendMessage(ChatColor.YELLOW + "Base value: " + ChatColor.WHITE + String.format("%.4f", attr.getBaseValue())
                + ChatColor.GRAY + " (vanilla default: 0.1000)");
        sender.sendMessage(ChatColor.YELLOW + "Final value: " + ChatColor.WHITE + String.format("%.4f", attr.getValue())
                + ChatColor.GRAY + " (effective speed)");
        sender.sendMessage(ChatColor.YELLOW + "Walk speed: " + ChatColor.WHITE + String.format("%.4f", target.getWalkSpeed()));

        var modifiers = attr.getModifiers();
        if (modifiers.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "  (no modifiers)");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Modifiers (" + modifiers.size() + "):");
            for (AttributeModifier mod : modifiers) {
                String op = switch (mod.getOperation()) {
                    case ADD_NUMBER -> "ADD";
                    case ADD_SCALAR -> "ADD%";
                    case MULTIPLY_SCALAR_1 -> "MULT";
                };
                sender.sendMessage(ChatColor.GRAY + "  " + mod.getName()
                        + ChatColor.WHITE + " " + String.format("%+.4f", mod.getAmount())
                        + ChatColor.DARK_GRAY + " [" + op + "]");
            }
        }

        // Relevant skill levels
        SkillManager sm = plugin.getSkillManager();
        if (sm != null) {
            int navLevel = sm.getSkillLevel(target, "Navigation");
            int bladeLevel = sm.getSkillLevel(target, "Blade Mastery");
            sender.sendMessage(ChatColor.YELLOW + "Skill levels: "
                    + ChatColor.WHITE + "Navigation=" + navLevel
                    + " BladeMastery=" + bladeLevel);
            if (navLevel > 0) {
                sender.sendMessage(ChatColor.GRAY + "  Nav speed bonus: +" + String.format("%.0f%%", navLevel * 1.0)
                        + " (1% per level)");
            }
        }

        // Active potion effects related to speed
        target.getActivePotionEffects().stream()
                .filter(e -> e.getType().getName().contains("SPEED") || e.getType().getName().contains("SLOW"))
                .forEach(e -> sender.sendMessage(ChatColor.YELLOW + "Potion: " + ChatColor.WHITE
                        + e.getType().getName() + " amp=" + e.getAmplifier()
                        + " dur=" + (e.getDuration() / 20) + "s"));
    }

    private void handleScalingPreview(CommandSender sender, String[] args) {
        CombatConfig combatConfig = plugin.getCombatConfig();
        if (combatConfig == null) {
            sender.sendMessage(ChatColor.RED + "CombatConfig not loaded.");
            return;
        }

        Map<String, ScalingFormula.ScalingParams> allParams = combatConfig.getAllScalingParams();

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Use: /cerb scalingpreview <all | skill/stat>");
            sender.sendMessage(ChatColor.GRAY + "Available: " + String.join(", ", allParams.keySet()));
            return;
        }

        String query = args[1].toLowerCase();

        if (query.equals("all")) {
            // Dump all scaling curves
            for (Map.Entry<String, ScalingFormula.ScalingParams> entry : allParams.entrySet()) {
                sender.sendMessage(ScalingFormula.preview(entry.getKey(), entry.getValue()));
            }
            return;
        }

        // Try to match by partial name — "crit-chance" matches "critical-strike / crit-chance"
        // Also support "critical-strike/crit-chance" or "critical-strike crit-chance"
        String searchKey = query.replace("/", " / ").replace("  ", " ");
        if (args.length >= 3) {
            searchKey = args[1].toLowerCase() + " / " + args[2].toLowerCase();
        }

        String foundKey = null;
        for (String key : allParams.keySet()) {
            if (key.equalsIgnoreCase(searchKey) || key.toLowerCase().contains(searchKey)) {
                foundKey = key;
                break;
            }
        }

        if (foundKey != null) {
            sender.sendMessage(ScalingFormula.preview(foundKey, allParams.get(foundKey)));
        } else {
            sender.sendMessage(ChatColor.RED + "No scaling stat found matching: " + searchKey);
            sender.sendMessage(ChatColor.GRAY + "Available: " + String.join(", ", allParams.keySet()));
        }
    }

    /**
     * /cerb spawn <mobId> — spawn a Cerberus-registered mob at the player's location.
     * The spawned mob is tagged with cerb_mob_id so MobRegistry.resolve() returns the correct tier.
     */
    private void handleSpawn(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(ChatColor.RED + "Use: /cerb spawn <mobId|vanilla mob> [level] [noai]");
            Manager.MobRegistry registry = plugin.getMobRegistry();
            if (registry != null) {
                player.sendMessage(ChatColor.GRAY + "Available: " + String.join(", ", registry.getEntryIds()));
            }
            return;
        }

        Manager.MobRegistry registry = plugin.getMobRegistry();
        if (registry == null) {
            player.sendMessage(ChatColor.RED + "MobRegistry not initialized.");
            return;
        }

        // /cerb spawn <mobId|entity> [level] [noai] - a mobs.yml id, or a vanilla living mob type.
        String mobId = args[1].toLowerCase();
        Manager.MobRegistry.MobEntry entry = registry.getEntry(mobId);
        org.bukkit.entity.EntityType type;
        String customName = null;
        Manager.Tier tier;
        if (entry != null) {
            type = entry.getEntityType();
            customName = entry.getCustomNameExact();
            tier = entry.getTier();
            if (type == null || type.getEntityClass() == null
                    || !org.bukkit.entity.LivingEntity.class.isAssignableFrom(type.getEntityClass())) {
                player.sendMessage(ChatColor.RED + "Mob '" + mobId + "' has no living entity-type in mobs.yml - cannot spawn.");
                return;
            }
        } else {
            type = org.bukkit.Registry.ENTITY_TYPE.get(org.bukkit.NamespacedKey.minecraft(mobId));
            if (type == null || type.getEntityClass() == null
                    || !org.bukkit.entity.Mob.class.isAssignableFrom(type.getEntityClass())) {
                player.sendMessage(ChatColor.RED + "No mobs.yml id or vanilla mob called '" + mobId + "'.");
                player.sendMessage(ChatColor.GRAY + "mobs.yml: " + String.join(", ", registry.getEntryIds()));
                return;
            }
            Manager.MobRegistry.MobEntry byType = registry.getEntryByType(type);
            tier = byType != null ? byType.getTier() : Manager.Tier.COMMON;
            mobId = null; // resolved through the registry like any natural spawn
        }
        if (Manager.MobTestService.TERRAIN_DESTROYING.contains(type)) {
            player.sendMessage(ChatColor.RED + "Not spawning " + type.name() + " from the command: its vanilla behaviour destroys terrain.");
            return;
        }

        int level = 0; // 0 = normal distance-based level
        boolean ai = true;
        for (int i = 2; i < args.length; i++) {
            if (args[i].equalsIgnoreCase("noai")) { ai = false; continue; }
            try { level = Math.max(1, Integer.parseInt(args[i])); }
            catch (NumberFormatException e) { player.sendMessage(ChatColor.RED + "Use: /cerb spawn <mob> [level] [noai]"); return; }
        }
        org.bukkit.Location spawnLoc = player.getLocation();
        // Command spawns are test spawns: never saved into the world, removed by panel cleanup.
        Manager.MobTestService.SpawnRequest req = new Manager.MobTestService.SpawnRequest(
                type, mobId, customName, level, ai, true, player.getUniqueId());
        int lvl = level;
        String shown = mobId != null ? mobId : type.name().toLowerCase();
        Manager.Tier shownTier = tier;

        // Spawn on the region thread owning the spawn location (Folia), through the shared
        // MobTestService: tag/name/level are applied before the spawn event so scaling sees them.
        Bukkit.getRegionScheduler().run(plugin, spawnLoc, task -> {
            try {
                plugin.getMobTestService().spawn(spawnLoc, req);
            } catch (RuntimeException ex) {
                player.sendMessage(ChatColor.RED + "Could not spawn " + shown + ": " + ex.getMessage());
                plugin.getLogger().warning("[Spawn] " + shown + " failed: " + ex);
                return;
            }
            player.sendMessage(ChatColor.GREEN + "Spawned " + ChatColor.GOLD + shown
                    + ChatColor.GREEN + " (" + shownTier.displayColor + shownTier.displayName + ChatColor.GREEN + ")"
                    + (lvl > 0 ? " at level " + lvl : "")
                    + " at " + ChatColor.WHITE + String.format("%.0f, %.0f, %.0f",
                    spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ()));
        });
    }
}

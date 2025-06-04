package Traps;

import Manager.CraftingManager;
import io.papermc.paper.event.entity.EntityMoveEvent;
import org.bukkit.entity.Entity;
import Skills.TrapMasterySkill;
import cerberus.world.cerb.CerberusPlugin;
import cerberus.world.cerb.CustomPlayer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import cerberus.world.cerb.CerberusWorldProtection;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.event.EventPriority;


import java.util.*;

public class TrapManager implements Listener {
    private final CerberusPlugin plugin;
    private final Map<UUID, Map<Location, Trap>> playerTraps;
    private final Map<Location, Trap> activeTraps;
    private final PatternDimensions patternDimensions;
    private final CraftingManager craftingManager;  // Add this field


    public TrapManager(CerberusPlugin plugin, CraftingManager craftingManager) {
        this.craftingManager = craftingManager;
        this.plugin = plugin;
        this.playerTraps = new HashMap<>();
        this.activeTraps = new HashMap<>();
        // Initialize with default dimensions for basic traps
        this.patternDimensions = new PatternDimensions(3, 2, 3, true,
                new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST});
    }

    // Enhanced trap potency system
    public void enhanceTrapPotency(Player player, double potencyBonus) {
        Map<Location, Trap> traps = playerTraps.get(player.getUniqueId());
        if (traps != null) {
            traps.values().forEach(trap -> trap.setPotencyMultiplier(1 + potencyBonus));
        }
    }

    // Enhanced trigger sensitivity
    public void enhanceTrapTrigger(Player player, double triggerSensitivityBonus) {
        Map<Location, Trap> traps = playerTraps.get(player.getUniqueId());
        if (traps != null) {
            traps.values().forEach(trap -> trap.setTriggerSensitivity(1 + triggerSensitivityBonus));
        }
    }

    // Enhanced duration system
    public void enhanceTrapDuration(Player player, double durationBonus) {
        Map<Location, Trap> traps = playerTraps.get(player.getUniqueId());
        if (traps != null) {
            traps.values().forEach(trap -> trap.setDurationMultiplier(1 + durationBonus));
        }
    }

    // Apply trap tag with type information
    public void applyTrapTagAutomatically(ItemStack item, TrapType trapType) {
        if (item != null && item.hasItemMeta()) {
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            container.set(
                    new NamespacedKey(plugin, "trap_type"),
                    PersistentDataType.STRING,
                    trapType.name()
            );
        }
    }

    // Enhanced trap item check
    public boolean isTrapItem(ItemStack item) {
        if (item != null && item.hasItemMeta()) {
            PersistentDataContainer container = item.getItemMeta().getPersistentDataContainer();
            return container.has(new NamespacedKey(plugin, "trap_type"), PersistentDataType.STRING);
        }
        return false;
    }

    // Get trap type from item
    public TrapType getTrapType(ItemStack item) {
        if (!isTrapItem(item)) return null;

        String trapTypeName = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "trap_type"), PersistentDataType.STRING);

        return TrapType.valueOf(trapTypeName);
    }

    public void registerTrapRecipes() {
        // Register basic snare recipe
        registerBasicSnareRecipe();
        // Register explosive trap recipe
        registerExplosiveTrapRecipe();
    }

    private void registerBasicSnareRecipe() {
        ItemStack snareItem = createTrapItem(TrapType.BASIC_SNARE);

        NamespacedKey key = new NamespacedKey(plugin, "basic_snare_trap");
        ShapedRecipe recipe = new ShapedRecipe(key, snareItem);

        recipe.shape("SWS", "WTW", "SWS");
        recipe.setIngredient('S', Material.STRING);
        recipe.setIngredient('W', Material.STICK);
        recipe.setIngredient('T', Material.TRIPWIRE_HOOK);

        plugin.getServer().addRecipe(recipe);
    }

    private void registerExplosiveTrapRecipe() {
        ItemStack explosiveItem = createTrapItem(TrapType.EXPLOSIVE_TRAP);

        NamespacedKey key = new NamespacedKey(plugin, "explosive_trap");
        ShapedRecipe recipe = new ShapedRecipe(key, explosiveItem);

        recipe.shape("RGR", "GTG", "RGR");
        recipe.setIngredient('R', Material.REDSTONE);
        recipe.setIngredient('G', Material.GUNPOWDER);
        recipe.setIngredient('T', Material.TNT);

        plugin.getServer().addRecipe(recipe);
    }

    private ItemStack createTrapItem(TrapType trapType) {
        ItemStack item = new ItemStack(trapType.getMaterial());
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(ChatColor.YELLOW + trapType.getDisplayName());

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.GRAY + "A powerful trap");
        lore.add(ChatColor.GREEN + "Right-click to place");

        meta.setLore(lore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);

        // Add trap data
        applyTrapTagAutomatically(item, trapType);

        // Apply crafting quality and bonuses
        craftingManager.applyCraftingBonuses(null, item, "TrapMastery");


        return item;
    }

    private void registerTrap(Trap trap) {
        activeTraps.put(trap.getLocation(), trap);
        playerTraps.computeIfAbsent(trap.getOwner().getUniqueId(), k -> new HashMap<>())
                .put(trap.getLocation(), trap);
    }

    public void onTrapTrigger(Trap trap, Entity target) {
        Player owner = trap.getOwner();

        // Use the static method from CustomPlayer
        CustomPlayer customPlayer = CustomPlayer.getCustomPlayer(owner);

        if (customPlayer != null) {
            TrapMasterySkill trapSkill = (TrapMasterySkill) customPlayer.getSkill("TrapMastery");
            if (trapSkill != null) {
                double expGain = calculateTrapExperience(trap, target);
                trapSkill.addExperience(expGain);
            }
        }
    }

    // ------------------------------------------------------------
// Trap trigger on entity movement (optimized)
// ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityMove(EntityMoveEvent event) {          // (custom event in your project)

        /* EARLY EXIT – no traps active */
        if (activeTraps.isEmpty()) return;

        Entity   entity   = event.getEntity();
        Location location = event.getTo();                     // where the entity moved to

        /* Iterate over armed traps only */
        for (Map.Entry<Location, Trap> entry : activeTraps.entrySet()) {

            Trap trap = entry.getValue();
            if (trap.getCurrentState() != TrapState.ARMED) continue;   // skip disarmed

            if (isInTrapRange(location, trap)) {
                triggerTrap(trap, entity);
            }
        }
    }


    // ------------------------------------------------------------
// Trap tamper protection on entity interact (optimized)
// ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onEntityInteract(EntityInteractEvent event) {

        Block block = event.getBlock();
        if (block == null) return;                           // EARLY EXIT

        Trap trap = getTrapAtLocation(block.getLocation());
        if (trap == null) return;                            // EARLY EXIT

        if (trap.getCurrentState() == TrapState.ARMED) {
            event.setCancelled(true);                        // stop default interaction
            triggerTrap(trap, event.getEntity());
        }
    }


    private boolean isInTrapRange(Location entityLoc, Trap trap) {
        double range = trap.getModifiedTriggerRadius(trap.getPattern().getTrapType().getTriggerRadius());
        return entityLoc.distance(trap.getLocation()) <= range;
    }

    private void triggerTrap(Trap trap, Entity target) {
        // Prevent double triggering
        if (trap.getCurrentState() != TrapState.ARMED) return;

        // Set trap state to activating
        trap.setState(TrapState.ACTIVATED);

        // Apply trap effects
        trap.applyTrapDamage(target);

        // Start cleanup after delay
        startTrapCleanup(trap);
    }

    private void startTrapCleanup(Trap trap) {
        new BukkitRunnable() {
            @Override
            public void run() {
                // Cleanup trap
                cleanupTrap(trap);
            }
        }.runTaskLater(plugin, 20L * 5); // 5 second delay
    }

    private void cleanupTrap(Trap trap) {
        Location loc = trap.getLocation();

        // Set final state
        trap.setState(TrapState.TRIGGERED);

        // Remove from tracking
        activeTraps.remove(loc);

        // Remove from player's traps
        Map<Location, Trap> playerTraps = this.playerTraps.get(trap.getOwner().getUniqueId());
        if (playerTraps != null) {
            playerTraps.remove(loc);
        }

        // Restore original blocks
        trap.getPattern().restoreBlocks(loc, trap.getFacing());
    }

    private double calculateTrapExperience(Trap trap, Entity target) {
        double baseExp = 10.0;  // Base experience for trap trigger

        // Modify based on target type
        if (target instanceof Player) baseExp *= 2.0;

        // Modify based on trap type
        switch(trap.getPattern().getTrapType()) {
            case EXPLOSIVE_TRAP -> baseExp *= 1.5;
            case BASIC_SNARE -> baseExp *= 1.0;
            // Add more cases
        }

        return baseExp;
    }

    // ------------------------------------------------------------
// Trap placement handler (optimized)
// ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {

        ItemStack item = event.getItemInHand();

        /* EARLY EXIT #1 – not a trap item */
        if (!isTrapItem(item)) return;

        /* EARLY EXIT #2 – (optional) ignore creative mode */
        // if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;

        event.setCancelled(true);                     // stop the normal block place

        TrapType trapType = getTrapType(item);
        if (trapType == null) return;                // EARLY EXIT #3 – unknown trap

        Location  location = event.getBlock().getLocation();
        Player    player   = event.getPlayer();
        BlockFace facing   = player.getFacing();

        if (canPlaceTrap(location, trapType, facing)) {
            placeTrap(player, location, trapType, facing);
            item.setAmount(item.getAmount() - 1);    // consume one trap item
        }
    }

    private boolean canPlaceTrap(Location location, TrapType trapType, BlockFace facing) {
        // Check world protection
        if (CerberusWorldProtection.isInProtectedRegion(location)) {
            return false;
        }

        // Check pattern dimensions
        return patternDimensions.validatePlacement(location, facing);
    }

    private void placeTrap(Player player, Location location, TrapType trapType, BlockFace facing) {
        TrapPattern pattern = TrapPattern.createPattern(trapType);
        // Add plugin as the fifth argument
        Trap trap = new Trap(player, location, pattern, facing, (CerberusPlugin) plugin);

        // Register trap
        activeTraps.put(location, trap);
        playerTraps.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(location, trap);

        // Apply pattern blocks
        pattern.applyPattern(location, facing);
    }

    // ------------------------------------------------------------
// Trap creation: auto‑tag result + apply skill bonuses
// ------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)   // <<< NEW flags
    public void onItemCraft(CraftItemEvent e) {

        /* ---------- EARLY‑EXIT FILTERS ---------- */
        if (e.getRecipe() == null) return;                                   // no recipe
        ItemStack result = e.getRecipe().getResult();
        if (result == null || result.getType().isAir()) return;              // nothing crafted

        TrapType trapType = getTrapTypeFromRecipe(result);
        if (trapType == null) return;                                        // not a trap recipe

        // ------------------------------------------------
        // 1) Clone & tag the result — Bukkit forbids      
        //    mutating the original ItemStack in the recipe
        // ------------------------------------------------
        ItemStack tagged = result.clone();                                   // <<< CLONE
        applyTrapTagAutomatically(tagged, trapType);                         // <<< tag PDC / lore
        e.getInventory().setResult(tagged);                                  // <<< put back

        // ------------------------------------------------
        // 2) Apply crafting bonuses (skill, luck, etc.)
        // ------------------------------------------------
        if (e.getWhoClicked() instanceof Player player) {
            craftingManager.applyCraftingBonuses(player, tagged, "TrapMastery");
            // ^ pass the *tagged* stack so meta is preserved
        }
    }


    private TrapType getTrapTypeFromRecipe(ItemStack item) {
        if (item.getType() == Material.TRIPWIRE_HOOK) {
            return TrapType.BASIC_SNARE;
        } else if (item.getType() == Material.TNT) {
            return TrapType.EXPLOSIVE_TRAP;
        }
        return null;
    }

    // Getters for trap information
    public Map<Location, Trap> getPlayerTraps(Player player) {
        return playerTraps.getOrDefault(player.getUniqueId(), new HashMap<>());
    }

    public Trap getTrapAtLocation(Location location) {
        return activeTraps.get(location);
    }

    public void cleanup() {
        // Clean up all active traps
        for (Trap trap : new ArrayList<>(activeTraps.values())) {
            cleanupTrap(trap);
        }

        // Clear maps
        activeTraps.clear();
        playerTraps.clear();
    }
}

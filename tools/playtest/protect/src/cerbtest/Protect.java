package cerbtest;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A stand-in for a protection plugin, for the sandbox only (tools/playtest/woodcutting.js protect): it refuses every
 * block break inside one box, the way claim plugins refuse breaks in a claim. Never shipped with Cerberus.
 * {@code /testprotect <x1> <y1> <z1> <x2> <y2> <z2>} sets the box, {@code /testprotect off} clears it.
 * {@code /testsave <player>} writes a player's data to disk now (Folia has no save-all; fishing.js crash-set uses it).
 * {@code /testnocatch <player>|off} refuses that player's catches (PlayerFishEvent CAUGHT_FISH, cancelled after Cerberus
 * decides), the way a minigame or claim plugin may (fishgear.js bait: a refused catch uses no bait).
 * {@code /testnoreel <player>|off} refuses that player's empty reel-ins (REEL_IN): the bobber stays out (fishgear.js bait).
 * {@code /testnodrops <player>|off} cancels the drops of that player's block breaks (BlockDropItemEvent, after Cerberus
 * at HIGH has seen them), the way a "no drops" plugin may: nothing drops, and nothing may pay (herbalism.js nodrops).
 * {@code /testdrops <player> clear|replace|double|off} changes that player's block drops at HIGHEST without cancelling,
 * after Cerberus's HIGH: clears them, replaces each with dirt, or doubles each amount (herbalism.js compat).
 * {@code /testbrew cancel|replace|off} cancels every brew, or replaces its results with water bottles, at HIGHEST after
 * Cerberus's own HIGHEST (the listener is registered when the command runs, after Cerberus's; alchemy.js compat).
 */
public final class Protect extends JavaPlugin implements Listener {

    private volatile int[] box;
    private volatile int refused;
    private final Set<UUID> noCatch = ConcurrentHashMap.newKeySet();
    private final Set<UUID> noReel = ConcurrentHashMap.newKeySet();
    private final Set<UUID> noDrops = ConcurrentHashMap.newKeySet();
    private final java.util.Map<UUID, String> dropMode = new ConcurrentHashMap<>();
    private volatile String brewMode = "off";
    private volatile boolean brewListening;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("testdrops")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("off")) { dropMode.clear(); sender.sendMessage("testdrops: off"); return true; }
            if (args.length != 2) return false;
            org.bukkit.entity.Player p = getServer().getPlayerExact(args[0]);
            if (p == null) { sender.sendMessage("testdrops: no such player"); return true; }
            String mode = args[1].toLowerCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("clear", "replace", "double", "off").contains(mode)) return false;
            if (mode.equals("off")) dropMode.remove(p.getUniqueId()); else dropMode.put(p.getUniqueId(), mode);
            sender.sendMessage("testdrops: " + p.getName() + " " + mode);
            return true;
        }
        if (command.getName().equalsIgnoreCase("testbrew")) {
            if (args.length != 1) return false;
            String mode = args[0].toLowerCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("cancel", "replace", "off").contains(mode)) return false;
            if (!brewListening) { // now, so it runs after Cerberus's own HIGHEST (registered after a delayed start)
                brewListening = true;
                getServer().getPluginManager().registerEvents(new Listener() {
                    @EventHandler(priority = EventPriority.HIGHEST)
                    public void onBrew(BrewEvent e) {
                        if (brewMode.equals("cancel")) e.setCancelled(true);
                        else if (brewMode.equals("replace")) {
                            for (int i = 0; i < e.getResults().size(); i++) {
                                ItemStack water = new ItemStack(org.bukkit.Material.POTION);
                                PotionMeta m = (PotionMeta) water.getItemMeta();
                                m.setBasePotionType(PotionType.WATER);
                                water.setItemMeta(m);
                                e.getResults().set(i, water);
                            }
                        }
                    }
                }, this);
            }
            brewMode = mode;
            sender.sendMessage("testbrew: " + mode);
            return true;
        }
        if (command.getName().equalsIgnoreCase("testnocatch")) {
            if (args.length != 1) return false;
            if (args[0].equalsIgnoreCase("off")) { noCatch.clear(); sender.sendMessage("testnocatch: off"); return true; }
            org.bukkit.entity.Player p = getServer().getPlayerExact(args[0]);
            if (p == null) return false;
            noCatch.add(p.getUniqueId());
            sender.sendMessage("testnocatch: refusing " + p.getName() + "'s catches");
            return true;
        }
        if (command.getName().equalsIgnoreCase("testnoreel")) {
            if (args.length != 1) return false;
            if (args[0].equalsIgnoreCase("off")) { noReel.clear(); sender.sendMessage("testnoreel: off"); return true; }
            org.bukkit.entity.Player p = getServer().getPlayerExact(args[0]);
            if (p == null) return false;
            noReel.add(p.getUniqueId());
            sender.sendMessage("testnoreel: refusing " + p.getName() + "'s reel-ins");
            return true;
        }
        if (command.getName().equalsIgnoreCase("testnodrops")) {
            if (args.length != 1) return false;
            if (args[0].equalsIgnoreCase("off")) { noDrops.clear(); sender.sendMessage("testnodrops: off"); return true; }
            org.bukkit.entity.Player p = getServer().getPlayerExact(args[0]);
            if (p == null) return false;
            noDrops.add(p.getUniqueId());
            sender.sendMessage("testnodrops: cancelling " + p.getName() + "'s block drops");
            return true;
        }
        if (command.getName().equalsIgnoreCase("testsave")) {
            org.bukkit.entity.Player p = args.length == 1 ? getServer().getPlayerExact(args[0]) : null;
            if (p == null) return false;
            p.getScheduler().run(this, t -> { p.saveData(); sender.sendMessage("testsave: saved " + p.getName()); }, null);
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("off")) {
            box = null;
            sender.sendMessage("testprotect: off (" + refused + " breaks refused)");
            return true;
        }
        if (args.length != 6) return false;
        int[] b = new int[6];
        try {
            for (int i = 0; i < 6; i++) b[i] = Integer.parseInt(args[i]);
        } catch (NumberFormatException e) {
            return false;
        }
        box = new int[] {Math.min(b[0], b[3]), Math.min(b[1], b[4]), Math.min(b[2], b[5]), Math.max(b[0], b[3]), Math.max(b[1], b[4]), Math.max(b[2], b[5])};
        refused = 0;
        sender.sendMessage("testprotect: protecting " + String.join(" ", args));
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        int[] b = box;
        if (b == null) return;
        int x = e.getBlock().getX(), y = e.getBlock().getY(), z = e.getBlock().getZ();
        if (x >= b[0] && y >= b[1] && z >= b[2] && x <= b[3] && y <= b[4] && z <= b[5]) {
            e.setCancelled(true);
            refused++;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent e) {
        if (noDrops.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
        String mode = dropMode.get(e.getPlayer().getUniqueId());
        if (mode == null) return;
        switch (mode) {
            case "clear" -> e.getItems().clear();
            case "replace" -> e.getItems().forEach(it -> it.setItemStack(new ItemStack(org.bukkit.Material.DIRT, it.getItemStack().getAmount())));
            case "double" -> e.getItems().forEach(it -> { ItemStack s = it.getItemStack(); s.setAmount(Math.min(s.getMaxStackSize(), s.getAmount() * 2)); it.setItemStack(s); });
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH && noCatch.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
        if (e.getState() == PlayerFishEvent.State.REEL_IN && noReel.contains(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }
}

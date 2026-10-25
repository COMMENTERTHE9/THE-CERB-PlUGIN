package Gathering;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Block breaks into gathering (docs/design/GATHERING.md). At HIGH a natural custom resource and the
 * wrong tool stop the break, and a custom resource's drops replace the vanilla ones; at MONITOR,
 * once nothing can cancel it any more, the XP is paid, custom drops fall and the placed mark is
 * forgotten. The Fortune bonus adds one item to what vanilla drops, in the drop event that follows
 * the break. Everything runs on the breaking player's region thread.
 */
public final class GatheringListener implements Listener {

    private final GatheringService service;
    /** Messages ("needs a drill") on the HUD: player, text. */
    private final BiConsumer<Player, String> notice;
    /** The break's evaluation, from HIGH to MONITOR (same event, same thread). */
    private final Map<UUID, GatheringService.Evaluation> deciding = new ConcurrentHashMap<>();
    /** A bonus to roll on the drops of the block just broken. */
    private final Map<UUID, GatheringService.Evaluation> bonus = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastNotice = new ConcurrentHashMap<>();

    public GatheringListener(GatheringService service, BiConsumer<Player, String> notice) {
        this.service = service;
        this.notice = notice;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakDecide(BlockBreakEvent e) {
        Player p = e.getPlayer();
        GatheringService.Evaluation ev = service.evaluate(p, e.getBlock());
        if (ev == null) { deciding.remove(p.getUniqueId()); return; }
        if (ev.decision().refuse()) {
            e.setCancelled(true);
            service.countRefusal();
            long now = System.currentTimeMillis();
            Long last = lastNotice.get(p.getUniqueId());
            if (last == null || now - last > 1500) {
                lastNotice.put(p.getUniqueId(), now);
                notice.accept(p, "§c" + ev.resource().id().replace('_', ' ') + " needs " + GatheringService.needs(ev.resource()));
            }
            deciding.remove(p.getUniqueId());
            return;
        }
        if (ev.decision().customDrops()) e.setDropItems(false);
        deciding.put(p.getUniqueId(), ev);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();
        GatheringService.Evaluation ev = deciding.remove(p.getUniqueId());
        if (ev == null || !ev.block().equals(b)) ev = service.evaluate(p, b); // un-cancelled after HIGH by another plugin
        if (ev == null) return;
        if (ev.decision().customDrops() && !e.isDropItems()) {
            boolean extra = service.rollBonus(ev);
            if (extra) service.countBonus();
            Location at = b.getLocation().add(0.5, 0.5, 0.5);
            for (ItemStack it : service.customDrops(ev.resource(), extra)) b.getWorld().dropItemNaturally(at, it);
        } else if (ev.decision().bonus()) {
            bonus.put(p.getUniqueId(), ev);
        }
        service.payXp(p, ev);
        if (service.placedBlocks() != null) service.placedBlocks().unmark(b);
    }

    /** The Fortune bonus: one more of the item vanilla drops (never the block itself, never a new item entity). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        GatheringService.Evaluation ev = bonus.remove(e.getPlayer().getUniqueId());
        if (ev == null || !ev.block().equals(e.getBlock()) || !service.rollBonus(ev)) return;
        for (Item item : e.getItems()) {
            ItemStack s = item.getItemStack();
            if (s.getType() == e.getBlockState().getType() || s.getAmount() >= s.getMaxStackSize()) continue;
            s.setAmount(s.getAmount() + 1);
            item.setItemStack(s);
            service.countBonus();
            e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.35f, 1.8f);
            return;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        deciding.remove(id);
        bonus.remove(id);
        lastNotice.remove(id);
        service.forget(id);
    }
}

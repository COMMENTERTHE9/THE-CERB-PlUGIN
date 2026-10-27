package Gathering;

import org.bukkit.Location;
import org.bukkit.Material;
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

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
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
    /**
     * A bonus to roll on the drops of the block just broken, with the item types vanilla drops for it:
     * the drop event also carries what blocks attached to it dropped (a torch on the ore), and the
     * bonus must never land on those.
     */
    private record Bonus(GatheringService.Evaluation ev, Set<Material> types) {}

    private final Map<UUID, Bonus> bonus = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastNotice = new ConcurrentHashMap<>();
    /** Woodcutting's techniques after a cut (heartwood, discoveries, Felling); null until wired. */
    private volatile Woodcutting woodcutting;

    public GatheringListener(GatheringService service, BiConsumer<Player, String> notice) {
        this.service = service;
        this.notice = notice;
    }

    public void setWoodcutting(Woodcutting w) { this.woodcutting = w; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreakDecide(BlockBreakEvent e) {
        if (e instanceof Woodcutting.FellingBreak) return; // a felling pays its own logs
        Player p = e.getPlayer();
        bonus.remove(p.getUniqueId()); // a new break: an earlier block's bonus never carries over
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
        if (e instanceof Woodcutting.FellingBreak) return;
        Player p = e.getPlayer();
        Block b = e.getBlock();
        bonus.remove(p.getUniqueId()); // also here: HIGH is skipped for a break another plugin cancelled and then allowed
        GatheringService.Evaluation ev = deciding.remove(p.getUniqueId());
        if (ev == null || !ev.block().equals(b)) ev = service.evaluate(p, b); // un-cancelled after HIGH by another plugin
        if (ev == null) return;
        if (ev.decision().customDrops() && !e.isDropItems()) {
            boolean extra = service.rollBonus(ev);
            if (extra) service.countBonus();
            Location at = b.getLocation().add(0.5, 0.5, 0.5);
            for (ItemStack it : service.customDrops(ev.resource(), extra)) b.getWorld().dropItemNaturally(at, it);
        } else if (ev.decision().bonus() && e.isDropItems()) {
            Set<Material> types = EnumSet.noneOf(Material.class);
            // A log drops itself, and Fortune for wood adds one more of it; an ore's bonus never goes to the block itself.
            boolean self = ev.resource().isTree();
            for (ItemStack d : b.getDrops(p.getInventory().getItemInMainHand(), p)) if (self || d.getType() != b.getType()) types.add(d.getType());
            if (!types.isEmpty()) bonus.put(p.getUniqueId(), new Bonus(ev, types));
        }
        service.payXp(p, ev);
        // A natural resource cut with a tool that fits it pays in survival: in creative, the HUD says why it didn't.
        if (ev.method() == GatherRules.Method.CREATIVE && !ev.placed() && (ev.toolFits() || ev.weakTool())) {
            service.withheld(p, ev.resource().skill(), Manager.XpIntegrity.whyNot(p));
        }
        Woodcutting w = woodcutting;
        if (w != null && ev.resource().isTree()) w.afterCut(p, ev); // before the unmark: it still reads the cut as placed or not
        if (service.placedBlocks() != null) service.placedBlocks().unmark(b);
    }

    /** The Fortune bonus: one more of an item the ore itself drops (never the block, never an attached block's drop, never a new item entity). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(BlockDropItemEvent e) {
        Bonus pending = bonus.remove(e.getPlayer().getUniqueId());
        if (pending == null || !pending.ev().block().equals(e.getBlock()) || !service.rollBonus(pending.ev())) return;
        for (Item item : e.getItems()) {
            ItemStack s = item.getItemStack();
            if (!pending.types().contains(s.getType()) || s.getAmount() >= s.getMaxStackSize()) continue;
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

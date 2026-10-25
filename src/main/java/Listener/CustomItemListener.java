package Listener;

import Spells.SpellManager;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles right-click casting for custom spell items (wands, rods, etc.).
 * Identifies spell items by PDC tag {@code cerb_spell} instead of display name.
 */
public class CustomItemListener implements Listener {

    private final SpellManager spellManager;
    private final NamespacedKey spellKey;
    private final JavaPlugin plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 1000; // 1 second between casts

    public CustomItemListener(SpellManager spellManager, JavaPlugin plugin) {
        this.spellManager = spellManager;
        this.spellKey = new NamespacedKey(plugin, "cerb_spell");
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // Check for cerb_spell PDC tag
        String spellName = meta.getPersistentDataContainer().get(spellKey, PersistentDataType.STRING);
        if (spellName == null) return;

        event.setCancelled(true); // prevent placing blaze rods etc.

        // Cooldown check
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long lastCast = cooldowns.get(uuid);
        long cooldown = plugin instanceof cerberus.world.cerb.CerberusPlugin cp && cp.getSecondaryEffects() != null
                ? cp.getSecondaryEffects().cooldownMs(uuid, COOLDOWN_MS) : COOLDOWN_MS; // Essence + Spell Weaving
        if (lastCast != null && (now - lastCast) < cooldown) {
            return; // silently ignore — no spam message
        }
        cooldowns.put(uuid, now);

        // Cast the spell
        spellManager.castSpell(spellName, player);
    }
}

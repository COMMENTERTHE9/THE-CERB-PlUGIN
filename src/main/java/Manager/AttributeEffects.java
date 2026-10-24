package Manager;

import Attributes.AttributeService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

/**
 * Pushes attribute-driven player stats into the systems that own them: Vitality → max health
 * and Essence → max mana and regeneration (here), Fortitude → armor, resistances and defense
 * bar (through {@link PlayerDefenseManager#updateDefense}). Runs when a player's skills load or change and
 * after a config reload. Combat reads the results from those systems as before.
 */
public final class AttributeEffects {

    /** Max health with no Vitality (the lifecycle engine's default). */
    public static final double BASE_MAX_HEALTH = HealthLifecycleEngine.DEFAULT_MAX_HP;

    private final JavaPlugin plugin;
    private final AttributeService attributes;
    private final PlayerVirtualHealthManager health;
    private final PlayerDefenseManager defense;
    private final PlayerManaManager mana;

    public AttributeEffects(JavaPlugin plugin, AttributeService attributes, PlayerVirtualHealthManager health,
                            PlayerDefenseManager defense, PlayerManaManager mana) {
        this.plugin = plugin;
        this.attributes = attributes;
        this.health = health;
        this.defense = defense;
        this.mana = mana;
    }

    /** Re-apply everything for one player. Call on the player's region thread. */
    public void refresh(Player player) {
        if (!attributes.isTracked(player.getUniqueId())) return; // not loaded, or already left
        applyMaxHealth(player);
        defense.updateDefense(player);
        applyMana(player);
    }

    /** Schedule {@link #refresh} on the player's own thread (safe from any thread). */
    public void refreshLater(UUID playerId) {
        Player p = Bukkit.getPlayer(playerId);
        if (p != null) p.getScheduler().run(plugin, t -> refresh(p), null);
    }

    /** After /cerb reload: everyone online, each on their own thread. */
    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) refreshLater(p.getUniqueId());
    }

    /** Max mana and regeneration with no Essence (PlayerManaManager defaults). */
    public static final double BASE_MAX_MANA = PlayerManaManager.DEFAULT_BASE_MANA;
    public static final double BASE_MANA_REGEN = PlayerManaManager.DEFAULT_MANA_REGEN_RATE;

    /**
     * Max mana and regeneration from Essence, keeping the same fraction of mana. Player thread.
     * Never creates mana state for a player who isn't tracked.
     */
    public void applyMana(Player player) {
        double[] m = mana.peekMana(player.getUniqueId());
        if (m == null) return;
        boolean on = attributes.rules().switches().essence();
        var s = attributes.get(player.getUniqueId());
        double newMax = BASE_MAX_MANA * (on ? s.maxManaMultiplier() : 1.0);
        double newRegen = BASE_MANA_REGEN * (on ? s.manaRegenMultiplier() : 1.0);
        if (Double.isFinite(newRegen)) mana.setManaRegenRate(player, newRegen);
        double oldMax = m[1];
        if (!Double.isFinite(newMax) || newMax <= 0 || Math.abs(newMax - oldMax) < 1e-9) return;
        mana.setMaxMana(player, newMax);
        if (oldMax > 0) mana.setPlayerCurrentMana(player, m[0] / oldMax * newMax);
    }

    /** What this player's max health should be right now. */
    public double maxHealthFor(UUID playerId) {
        var s = attributes.get(playerId);
        double mult = attributes.rules().switches().vitality() ? s.maxHpMultiplier() : 1.0;
        return BASE_MAX_HEALTH * s.realmMultiplier() * mult; // realm magnitude × Vitality
    }

    /**
     * Set max health from Vitality, keeping the same fraction of health (a full player stays
     * full). Player thread. Never creates health state for a player who isn't tracked.
     */
    public void applyMaxHealth(Player player) {
        HealthStateSnapshot hs = health.peekSnapshot(player.getUniqueId());
        if (hs == null) return;
        double newMax = maxHealthFor(player.getUniqueId());
        double oldMax = hs.maxHp();
        if (!Double.isFinite(newMax) || newMax <= 0 || Math.abs(newMax - oldMax) < 1e-9) return;
        health.setPlayerMaxVirtualHealth(player, newMax);
        if (hs.lifecycleState() == HealthLifecycleState.ALIVE && oldMax > 0) {
            health.setPlayerVirtualHealth(player, hs.currentHp() / oldMax * newMax);
        }
    }
}

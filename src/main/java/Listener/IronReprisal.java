package Listener;

import Manager.IronReprisalRules;
import Manager.PlayerDefenseManager;
import Manager.PvpTag;
import Skills.SkillManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Iron Reprisal, Heavy Armor Mastery's throw-back (docs/design/COMBAT_IMPROVEMENTS.md §2): a creature whose melee hit lands
 * on a player wearing heavy armor is thrown back, harder with the skill's level and each heavy piece worn. It runs on the
 * attacker's own region thread the tick after the hit, after the server's own knockback and movement, through the server's
 * knockback (the attacker's knockback resistance counts). No chance roll and no damage: nothing it does can start another hit.
 */
public final class IronReprisal implements Listener {

    public static final String SKILL = "Heavy Armor Training";
    /** An attacker farther than this from where the hit landed by its turn (teleported, pulled away) is left alone. */
    private static final double MAX_RANGE_SQ = 8 * 8;
    /** The cooldown map is swept of ended entries once it holds this many (bounded memory, no timer). */
    private static final int SWEEP_AT = 512;

    private final Plugin plugin;
    private final SkillManager skills;
    private final Supplier<IronReprisalRules> rules;
    /** Attacker → when it may be thrown again (System.nanoTime()). */
    private final Map<UUID, Long> nextAt = new ConcurrentHashMap<>();
    private final LongAdder thrown = new LongAdder(), notLanded = new LongAdder(), pvpOff = new LongAdder(),
            cooling = new LongAdder(), exempt = new LongAdder(), shield = new LongAdder(), gone = new LongAdder();

    public IronReprisal(Plugin plugin, SkillManager skills, Supplier<IronReprisalRules> rules) {
        this.plugin = plugin;
        this.skills = skills;
        this.rules = rules;
    }

    /** The current settings (combat.yml), never null. */
    public IronReprisalRules rules() {
        IronReprisalRules r = rules.get();
        return r == null ? IronReprisalRules.DEFAULTS : r;
    }

    // MONITOR, not cancelled: the hit stands after every other plugin had its say.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;
        DamageCause cause = e.getCause();
        if (cause != DamageCause.ENTITY_ATTACK && cause != DamageCause.ENTITY_SWEEP_ATTACK) return; // melee only
        if (!(e.getDamager() instanceof LivingEntity attacker) || attacker == victim) return;       // never a projectile or yourself
        IronReprisalRules r = rules();
        if (!r.enabled()) return;
        int pieces = heavyPieces(victim);
        if (pieces == 0) return;
        double strength = r.strength(skills.getSkillLevel(victim, SKILL), pieces);
        if (strength <= 0) return;
        // The shield took the blow: it never landed, so there is nothing to answer (the game itself pushes nobody on a block).
        // A blow from behind, past a raised shield, lands and is answered as any other.
        if (VirtualHealthListener.shieldTookAll(e)) { shield.increment(); return; }
        if (!VirtualHealthListener.landed(e)) { notLanded.increment(); return; } // invulnerable, countered or lethal: no hit to answer
        Player behind = PvpTag.playerBehind(attacker);
        if (behind != null) {
            if (behind.equals(victim)) return;                // your own animal
            if (!r.pvp()) { pvpOff.increment(); return; }     // PvP: off for now, not permanently
        }
        LivingEntity target = mount(attacker);
        if (!r.throwable(target.getType().name())) { exempt.increment(); return; }
        long now = System.nanoTime();
        Long next = nextAt.get(target.getUniqueId());
        if (next != null && now - next < 0) { cooling.increment(); return; }
        if (nextAt.size() >= SWEEP_AT) nextAt.values().removeIf(t -> now - t >= 0);
        nextAt.put(target.getUniqueId(), now + r.cooldownMs() * 1_000_000L);

        Location at = victim.getLocation();
        UUID world = at.getWorld().getUID();
        double vx = at.getX(), vz = at.getZ();
        float yaw = at.getYaw();
        boolean sound = r.sound();
        target.getScheduler().runDelayed(plugin, t -> push(target, world, vx, vz, yaw, strength, sound), null, 1L);
    }

    /** On {@code target}'s own thread, a tick after the hit: away from where the player stood. */
    private void push(LivingEntity target, UUID world, double vx, double vz, float yaw, double strength, boolean sound) {
        if (!target.isValid() || target.isDead() || !target.getWorld().getUID().equals(world)) { gone.increment(); return; }
        double dx = vx - target.getX(), dz = vz - target.getZ(); // towards the player: the server pushes the other way
        double d2 = dx * dx + dz * dz;
        if (d2 > MAX_RANGE_SQ) { gone.increment(); return; }
        if (d2 < 1e-6) { // on the player's spot: along where the player faces
            double rad = Math.toRadians(yaw);
            dx = Math.sin(rad);
            dz = -Math.cos(rad);
        }
        target.knockback(strength, dx, dz);
        // A player's own client moves them: the server's knockback alone never reaches it, so the push is sent as their velocity.
        if (target instanceof Player pl) pl.setVelocity(pl.getVelocity());
        thrown.increment();
        if (sound) target.getWorld().playSound(target.getLocation(), Sound.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 0.5f, 0.75f);
    }

    /** What moves: the attacker, or the creature it rides (a spider jockey's spider). */
    private static LivingEntity mount(LivingEntity attacker) {
        Entity e = attacker;
        for (int i = 0; i < 4 && e.getVehicle() != null; i++) e = e.getVehicle();
        return e instanceof LivingEntity le ? le : attacker;
    }

    /** Heavy armor pieces worn (iron, diamond, netherite helmet, chestplate, leggings, boots): 0 to 4. */
    public static int heavyPieces(Player p) {
        int n = 0;
        for (ItemStack piece : p.getInventory().getArmorContents()) {
            if (piece != null && isHeavyPiece(piece.getType())) n++;
        }
        return n;
    }

    static boolean isHeavyPiece(Material m) {
        String name = m.name();
        return PlayerDefenseManager.isHeavyArmor(m)
                && (name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS"));
    }

    // ── admin: /cerb reprisal [player|reset] ─────────────────────

    public void admin(CommandSender sender, String[] args) {
        IronReprisalRules r = rules();
        if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
            for (LongAdder a : new LongAdder[]{thrown, notLanded, pvpOff, cooling, exempt, shield, gone}) a.reset();
            sender.sendMessage(ChatColor.GREEN + "Iron Reprisal counters reset.");
            return;
        }
        sender.sendMessage(ChatColor.GOLD + "Iron Reprisal: " + (r.enabled() ? "on" : "off") + ChatColor.GRAY
                + " · from Heavy Armor Mastery " + r.unlockLevel() + ", push " + r.strengthAtUnlock() + " → " + r.strengthAtMax()
                + " at " + r.maxLevel() + " (four heavy pieces, a quarter each) · cooldown " + r.cooldownMs() + " ms · PvP "
                + (r.pvp() ? "on" : "off") + " · exempt " + r.exempt());
        sender.sendMessage(ChatColor.GRAY + "Thrown " + thrown.sum() + " · not thrown: hit not landed " + notLanded.sum()
                + ", PvP off " + pvpOff.sum() + ", cooldown " + cooling.sum() + ", exempt " + exempt.sum()
                + ", the shield took it " + shield.sum() + ", gone by its turn " + gone.sum());
        if (args.length < 2) return;
        Player p = Bukkit.getPlayerExact(args[1]);
        if (p == null) { sender.sendMessage(ChatColor.RED + "Player not online: " + args[1]); return; }
        p.getScheduler().run(plugin, t -> {
            int level = skills.getSkillLevel(p, SKILL), pieces = heavyPieces(p);
            sender.sendMessage(ChatColor.GRAY + p.getName() + ": Heavy Armor Mastery " + level + ", heavy pieces " + pieces
                    + ", push " + String.format(java.util.Locale.ROOT, "%.3f", r.strength(level, pieces)));
        }, null);
    }
}

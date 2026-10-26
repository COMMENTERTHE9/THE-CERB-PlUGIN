package Skills;

import Attributes.AttributeService;
import Attributes.AttributeSnapshot;
import Manager.PlayerHUDManager;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Level-up and rank-up presentation (docs/design/LEVEL_UP.md). Skill levels, skill milestones and class ranks are
 * queued here, from any thread; one tick later everything the player earned meanwhile is shown once, on their own
 * thread: chat lines, a notice in the middle of the HUD bar (below ability messages, above the XP line), and in full
 * mode a sound, a small particle effect and, for a milestone or a new ability, a title. Effects play at most once every
 * {@link #EFFECT_GAP_MS} per player; text always shows. Nothing runs every tick.
 *
 * <p>Only real progress arrives here: loading data and admin corrections don't go through it ({@code SkillManager}'s
 * quiet path, {@code ClassService.setRank}). Each player picks Full, Reduced or Off with {@code /levelup}; the choice
 * is stored on the player (saved with their player data, no database write).
 */
public final class Celebrations implements Listener, TabExecutor {

    public static final NamespacedKey MODE_KEY = new NamespacedKey("cerberus", "levelup");
    /** Sound, particles and titles at most this often per player (a burst of level-ups never becomes a storm). */
    static final long EFFECT_GAP_MS = 1_500;
    /** How long the HUD notice stays. */
    static final long NOTICE_MS = 3_000;

    public enum Mode {
        FULL, REDUCED, OFF;

        public String id() { return name().toLowerCase(Locale.ROOT); }

        public static Mode parse(String s) {
            if (s == null) return null;
            for (Mode m : values()) if (m.id().equalsIgnoreCase(s.trim())) return m;
            return null;
        }
    }

    /** Abilities that a skill's new levels made available (ClassService: their other requirements just got met). */
    @FunctionalInterface
    public interface AbilityUnlocks {
        List<Celebration> unlockedBy(Player p, String skill, int from, int to);
    }

    /** One skill's levels gained in this batch. */
    private record SkillLevels(String skill, int from, int to) {}

    private static final class State {
        final List<Object> pending = new ArrayList<>(); // SkillLevels or Celebration, in the order earned
        boolean scheduled;
        long lastEffect;
        volatile Mode mode;
    }

    private final Plugin plugin;
    private final PlayerHUDManager hud;
    private final AttributeService attributes;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private final LongAdder shown = new LongAdder(), effects = new LongAdder();
    private volatile AbilityUnlocks unlocks = (p, skill, from, to) -> List.of();

    public Celebrations(Plugin plugin, PlayerHUDManager hud, AttributeService attributes) {
        this.plugin = plugin;
        this.hud = hud;
        this.attributes = attributes;
    }

    public void setAbilityUnlocks(AbilityUnlocks unlocks) { this.unlocks = unlocks; }

    // ── what arrives (any thread) ─────────────────────────────────

    /** {@code skill} went from level {@code from} to {@code to} through real play. */
    public void skillLevels(Player p, String skill, int from, int to) {
        if (p != null && to > from) queue(p, new SkillLevels(skill, from, to));
    }

    /** Anything else worth celebrating (a class rank; later realm breakthroughs, trials, Awakenings). */
    public void celebrate(Player p, Celebration c) {
        if (p != null && c != null) queue(p, c);
    }

    private void queue(Player p, Object event) {
        State s = state(p);
        boolean schedule;
        synchronized (s) {
            s.pending.add(event);
            schedule = !s.scheduled;
            s.scheduled = true;
        }
        if (schedule) p.getScheduler().runDelayed(plugin, t -> flush(p, s), () -> states.remove(p.getUniqueId()), 1L);
    }

    // ── shown one tick later (player's thread) ───────────────────

    private void flush(Player p, State s) {
        List<Object> events;
        synchronized (s) {
            events = new ArrayList<>(s.pending);
            s.pending.clear();
            s.scheduled = false;
        }
        if (!p.isOnline() || events.isEmpty()) return;
        List<Celebration> all = new ArrayList<>();
        for (SkillLevels lv : mergeLevels(events)) {
            all.add(skillCelebration(p, lv));
            all.addAll(unlocks.unlockedBy(p, lv.skill(), lv.from(), lv.to()));
        }
        for (Object e : events) if (e instanceof Celebration c) all.add(c);
        Celebration.Batch b = Celebration.combine(all);
        if (b == null) return;
        present(p, s, b);
    }

    /** A skill's levels reached in several steps of one batch become one: from the first "from" to the last "to". */
    private static List<SkillLevels> mergeLevels(List<Object> events) {
        Map<String, SkillLevels> by = new java.util.LinkedHashMap<>();
        for (Object e : events) {
            if (!(e instanceof SkillLevels lv)) continue;
            by.merge(lv.skill(), lv, (a, b) -> new SkillLevels(a.skill(), Math.min(a.from(), b.from()), Math.max(a.to(), b.to())));
        }
        return new ArrayList<>(by.values());
    }

    /** "▲ Blade Mastery 12 · +0.9 Power (swords)", or a milestone with its title. */
    Celebration skillCelebration(Player p, SkillLevels lv) {
        String name = SkillNames.display(lv.skill());
        List<LevelUps.Gain> gains = gains(p, lv);
        String levels = LevelUps.levels(lv.from(), lv.to());
        String best = gains.isEmpty() ? "" : "§7 · §b" + gains.get(0).text();
        StringBuilder detail = new StringBuilder();
        for (LevelUps.Gain g : gains) detail.append(detail.isEmpty() ? "§7: " : "§7, ").append("§b").append(g.fromTo());
        int milestone = LevelUps.milestoneCrossed(lv.from(), lv.to());
        if (milestone > 0) {
            String title = LevelUps.title(milestone);
            return new Celebration(Celebration.Kind.SKILL_MILESTONE, Celebration.Tier.MAJOR,
                    "§6★ §f" + name + " §6" + levels + "§7 · §6" + title + best, "§6★ §f" + name + " §6" + levels,
                    "§6" + title, "§e" + name + " " + milestone,
                    List.of("§6★ §f" + name + " §7reached level §6" + lv.to() + " §7· §6" + title + detail), -1);
        }
        return new Celebration(Celebration.Kind.SKILL_LEVEL, Celebration.Tier.ORDINARY,
                "§a▲ §f" + name + " §a" + levels + best, "§a▲ §f" + name + " §a" + levels, null, null,
                List.of("§a▲ §f" + name + " §7reached level §a" + lv.to() + detail), -1);
    }

    /** The real gain: the player's attributes with the skill at its old level and at its new one (diminishing returns included). */
    private List<LevelUps.Gain> gains(Player p, SkillLevels lv) {
        AttributeService svc = attributes;
        UUID id = p.getUniqueId();
        if (svc == null || !svc.isTracked(id)) return List.of();
        AttributeSnapshot before = svc.withSkillLevel(id, lv.skill(), lv.from());
        AttributeSnapshot after = svc.withSkillLevel(id, lv.skill(), lv.to());
        return LevelUps.gains(lv.skill(), svc.rules(), before, after);
    }

    private void present(Player p, State s, Celebration.Batch b) {
        shown.increment();
        for (String line : b.chat()) p.sendMessage(line);
        Mode mode = s.mode;
        if (mode == Mode.OFF) return;
        if (!b.hud().isEmpty()) hud.notice(p, b.hud(), NOTICE_MS);
        long now = System.currentTimeMillis();
        if (now - s.lastEffect < EFFECT_GAP_MS) return;
        s.lastEffect = now;
        effects.increment();
        if (mode == Mode.REDUCED) {
            p.playSound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 0.35f, 1.6f);
            return;
        }
        sound(p, b);
        particles(p, b);
        if (b.title() != null) {
            p.showTitle(Title.title(LEGACY.deserialize(b.title()), LEGACY.deserialize(b.subtitle() == null ? "" : b.subtitle()),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofMillis(1800), Duration.ofMillis(500))));
        }
    }

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** The player alone hears it. */
    private static void sound(Player p, Celebration.Batch b) {
        switch (b.kind()) {
            case CLASS_RANK -> {
                p.playSound(p, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.25f);
                if (b.tier() != Celebration.Tier.ORDINARY) p.playSound(p, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.8f, 1.0f);
            }
            case SKILL_LEVEL -> p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.4f);
            default -> {
                p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.1f);
                p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.55f, 1.0f);
            }
        }
    }

    /** Players nearby see it. Bounded: about 12 particles for a level, 36 for a milestone, 30 for a class rank. */
    private static void particles(Player p, Celebration.Batch b) {
        Location at = p.getLocation();
        World w = p.getWorld();
        if (b.kind() == Celebration.Kind.CLASS_RANK) {
            Color c = Color.fromRGB(b.rgb() >= 0 ? b.rgb() & 0xFFFFFF : 0xE0C060);
            ring(w, at.clone().add(0, 0.15, 0), 0.9, 20, Particle.DUST, new Particle.DustOptions(c, 1.3f));
            w.spawnParticle(Particle.ENCHANT, at.clone().add(0, 1.3, 0), 10, 0.4, 0.4, 0.4, 0.6);
            return;
        }
        if (b.tier() == Celebration.Tier.ORDINARY) {
            w.spawnParticle(Particle.HAPPY_VILLAGER, at.clone().add(0, 1.0, 0), 8, 0.35, 0.45, 0.35, 0);
            w.spawnParticle(Particle.END_ROD, at.clone().add(0, 1.1, 0), 4, 0.2, 0.3, 0.2, 0.02);
            return;
        }
        ring(w, at.clone().add(0, 0.2, 0), 1.0, 16, Particle.END_ROD, null);
        w.spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0, 1.2, 0), 20, 0.35, 0.6, 0.35, 0.25);
    }

    private static <T> void ring(World w, Location center, double radius, int points, Particle type, T data) {
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            Location l = center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            if (data == null) w.spawnParticle(type, l, 1, 0, 0, 0, 0);
            else w.spawnParticle(type, l, 1, 0, 0, 0, 0, data);
        }
    }

    // ── the setting ───────────────────────────────────────────────

    private State state(Player p) {
        return states.computeIfAbsent(p.getUniqueId(), id -> {
            State s = new State();
            s.mode = stored(p);
            return s;
        });
    }

    private static Mode stored(Player p) {
        Mode m = Mode.parse(p.getPersistentDataContainer().get(MODE_KEY, PersistentDataType.STRING));
        return m != null ? m : Mode.FULL;
    }

    /** The player's setting (Full until they choose). */
    public Mode mode(Player p) { return state(p).mode; }

    /** Change the player's setting. Player's thread. */
    public void setMode(Player p, Mode m) {
        p.getPersistentDataContainer().set(MODE_KEY, PersistentDataType.STRING, m.id());
        state(p).mode = m;
    }

    /** Presentations shown and effects played since start (diagnostics, load tests). */
    public long shown() { return shown.sum(); }
    public long effects() { return effects.sum(); }

    // ── /levelup [full|reduced|off] ──────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players see level-ups.");
            return true;
        }
        Mode m;
        if (args.length == 0) {
            m = Mode.values()[(mode(p).ordinal() + 1) % Mode.values().length];
        } else {
            m = Mode.parse(args[0]);
            if (m == null) {
                p.sendMessage(ChatColor.RED + "Use: /" + label + " [full|reduced|off]");
                return true;
            }
        }
        setMode(p, m);
        p.sendMessage(ChatColor.GOLD + "Level-ups: " + ChatColor.YELLOW + Character.toUpperCase(m.id().charAt(0)) + m.id().substring(1)
                + ChatColor.GRAY + switch (m) {
                    case FULL -> " - a sound, a small effect, a line in your HUD and in chat; milestones and new abilities get a title.";
                    case REDUCED -> " - a line in your HUD and in chat, and a quiet sound. No effects or titles.";
                    case OFF -> " - only the line in chat.";
                });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (Mode m : Mode.values()) if (m.id().startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(m.id());
        }
        return out;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        states.remove(e.getPlayer().getUniqueId());
    }
}

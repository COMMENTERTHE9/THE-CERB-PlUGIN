package Skills;

import Manager.PlayerHUDManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * The real-time XP display (owner request, docs/design/GATHERING.md): every XP gain shows briefly
 * in the middle of the HUD bar, gains of a skill a few seconds apart merged into one line, below
 * ability and combat messages in priority. Each player picks Compact, Detailed or Off with
 * {@code /xpdisplay}; the choice is stored on the player (saved with their player data, no
 * database write). Level-ups stay in chat. Work happens on the player's own thread.
 */
public final class XpDisplay implements Listener, TabExecutor, SkillManager.XpListener {

    public static final NamespacedKey MODE_KEY = new NamespacedKey("cerberus", "xp_display");
    /** Gains of the same skill this close together add up. */
    static final long MERGE_MS = 4_000;
    /** How long the line stays after the last gain. */
    static final long SHOW_MS = 2_500;
    /** Why an action paid nothing (creative mode, a spawn-egg mob): shown this long, at most this often per skill and reason. */
    static final long WITHHELD_SHOW_MS = 4_000, WITHHELD_EVERY_MS = 5 * 60_000;

    private static final class State {
        volatile XpLine.Mode mode;
        final XpLine line = new XpLine(MERGE_MS);
        /** When each withheld-XP notice ("skill|why") was last shown. */
        final Map<String, Long> withheld = new ConcurrentHashMap<>();
    }

    private final Plugin plugin;
    private final PlayerHUDManager hud;
    private final Map<UUID, State> states = new ConcurrentHashMap<>();
    private final LongAdder updates = new LongAdder();

    public XpDisplay(Plugin plugin, PlayerHUDManager hud) {
        this.plugin = plugin;
        this.hud = hud;
    }

    @Override
    public void gained(Player p, String skill, long amount, SkillProgression.Result after) {
        if (Bukkit.isOwnedByCurrentRegion(p)) show(p, skill, amount, after);
        else p.getScheduler().run(plugin, t -> show(p, skill, amount, after), null);
    }

    private void show(Player p, String skill, long amount, SkillProgression.Result after) {
        if (!p.isOnline()) return;
        State s = state(p);
        XpLine.Mode mode = s.mode;
        if (mode == XpLine.Mode.OFF) return;
        boolean max = after.level() >= SkillProgression.MAX_LEVEL;
        s.line.add(SkillNames.display(skill), amount, after.level(), after.xp(), SkillManager.xpForLevel(after.level()), max, System.nanoTime());
        hud.xp(p, s.line.render(mode), SHOW_MS);
        updates.increment();
    }

    @Override
    public void withheld(Player p, String skill, String why) {
        if (Bukkit.isOwnedByCurrentRegion(p)) explain(p, skill, why);
        else p.getScheduler().run(plugin, t -> explain(p, skill, why), null);
    }

    /** The withheld-XP notice in the XP line's place, once per skill and reason every few minutes; nothing when the display is off. */
    private void explain(Player p, String skill, String why) {
        if (!p.isOnline()) return;
        State s = state(p);
        if (s.mode == XpLine.Mode.OFF) return;
        if (!due(s.withheld, skill + '|' + why, System.currentTimeMillis(), WITHHELD_EVERY_MS)) return;
        hud.xp(p, withheldLine(SkillNames.display(skill), why), WITHHELD_SHOW_MS);
        updates.increment();
    }

    /** True, and noted in {@code shown}, when notice {@code key} wasn't shown in the last {@code everyMs}. */
    static boolean due(Map<String, Long> shown, String key, long now, long everyMs) {
        Long last = shown.get(key);
        if (last != null && now - last < everyMs) return false;
        shown.put(key, now);
        return true;
    }

    /** "No Fishing XP in creative mode" (section-sign colours). */
    static String withheldLine(String skill, String why) {
        return "§7No §f" + skill + "§7 XP " + why;
    }

    private State state(Player p) {
        return states.computeIfAbsent(p.getUniqueId(), id -> {
            State s = new State();
            s.mode = stored(p);
            return s;
        });
    }

    /** The player's setting (Compact until they choose). */
    public XpLine.Mode mode(Player p) {
        return state(p).mode;
    }

    private static XpLine.Mode stored(Player p) {
        XpLine.Mode m = XpLine.Mode.parse(p.getPersistentDataContainer().get(MODE_KEY, PersistentDataType.STRING));
        return m != null ? m : XpLine.Mode.COMPACT;
    }

    /** Change the player's setting. Player's thread. */
    public void setMode(Player p, XpLine.Mode m) {
        p.getPersistentDataContainer().set(MODE_KEY, PersistentDataType.STRING, m.id());
        State s = state(p);
        s.mode = m;
        hud.xp(p, m == XpLine.Mode.OFF ? "" : s.line.render(m), SHOW_MS);
    }

    /** Lines shown since start (diagnostics). */
    public long updates() { return updates.sum(); }

    // ── /xpdisplay [compact|detailed|off] ────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players see the XP display.");
            return true;
        }
        XpLine.Mode m;
        if (args.length == 0) {
            XpLine.Mode now = mode(p);
            m = XpLine.Mode.values()[(now.ordinal() + 1) % XpLine.Mode.values().length];
        } else {
            m = XpLine.Mode.parse(args[0]);
            if (m == null) {
                p.sendMessage(ChatColor.RED + "Use: /" + label + " [compact|detailed|off]");
                return true;
            }
        }
        setMode(p, m);
        p.sendMessage(ChatColor.GOLD + "XP display: " + ChatColor.YELLOW + Character.toUpperCase(m.id().charAt(0)) + m.id().substring(1)
                + ChatColor.GRAY + switch (m) {
                    case COMPACT -> " - XP gains show briefly in your HUD, e.g. +86 Mining.";
                    case DETAILED -> " - XP gains with your progress, e.g. +86 Mining XP · 1,240/2,000 · Lv 12.";
                    case OFF -> " - no XP in the HUD. Level-ups still show in chat.";
                });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (XpLine.Mode m : XpLine.Mode.values()) if (m.id().startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(m.id());
        }
        return out;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        states.remove(e.getPlayer().getUniqueId());
    }
}

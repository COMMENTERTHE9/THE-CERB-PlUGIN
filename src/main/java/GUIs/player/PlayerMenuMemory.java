package GUIs.player;

import GUIs.SkillDefinitions;
import GUIs.SkillDefinitions.SkillCategory;
import GUIs.SkillDefinitions.SkillDef;
import Skills.SkillManager;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * What a player last saw in the Player Menu, kept in memory for this session only (never saved, no
 * database work): their skill levels and XP when they last closed it, and the skill category they
 * last opened. Screens use it to point at what changed since ("▲ +2 levels since you last looked")
 * and to animate that progress once. Before the first close of a session nothing counts as new.
 */
public final class PlayerMenuMemory {

    /** A skill's level and the XP into that level. */
    public record Seen(int level, int xp) {}

    private final Map<UUID, Map<String, Seen>> lastClosed = new ConcurrentHashMap<>();
    /** What the last screen drawn showed; becomes {@code lastClosed} when the menu closes. */
    private final Map<UUID, Map<String, Seen>> lastShown = new ConcurrentHashMap<>();
    private final Map<UUID, SkillCategory> lastCategory = new ConcurrentHashMap<>();
    /** Players already told (with a sound) about the progress since their last close. */
    private final Set<UUID> announced = ConcurrentHashMap.newKeySet();
    /** Skills whose level-up each player has already been shown since their last close. */
    private final Map<UUID, Set<String>> looked = new ConcurrentHashMap<>();

    /** The skill as the player last saw it, or null before their first close this session. */
    public Seen seen(UUID player, String skill) {
        Map<String, Seen> m = lastClosed.get(player);
        return m == null ? null : m.get(skill);
    }

    /** Levels gained in {@code skill} since the last close (0 before the first close). */
    public int gained(UUID player, String skill, int levelNow) {
        Seen s = seen(player, skill);
        return s == null ? 0 : Math.max(0, levelNow - s.level());
    }

    /** Levels gained across all catalogued skills since the last close. */
    public int gainedTotal(UUID player, SkillManager sm) {
        Map<String, Seen> m = lastClosed.get(player);
        if (m == null) return 0;
        int sum = 0;
        for (SkillDef d : SkillDefinitions.all()) {
            Seen s = m.get(d.name());
            if (s != null) sum += Math.max(0, sm.getSkillLevel(player, d.name()) - s.level());
        }
        return sum;
    }

    /** True until the "new progress" chime has played since the last close (it plays once, not on every screen). */
    public boolean wouldAnnounce(UUID player) {
        return !announced.contains(player);
    }

    /** The chime really played (called from the animation, so a cut-short animation doesn't use it up). */
    public void markAnnounced(UUID player) {
        announced.add(player);
    }

    /** True until a skill's level-up flash has played since the last close. */
    public boolean isFirstLook(UUID player, String skill) {
        Set<String> s = looked.get(player);
        return s == null || !s.contains(skill);
    }

    /** The flash really played (called from the animation). */
    public void markLooked(UUID player, String skill) {
        looked.computeIfAbsent(player, k -> ConcurrentHashMap.newKeySet()).add(skill);
    }

    /** A screen was drawn: these are the values the player has now been shown. */
    public void shown(Player p, SkillManager sm) {
        Map<String, Seen> now = snapshot(p.getUniqueId(), sm);
        if (now != null) lastShown.put(p.getUniqueId(), now);
    }

    /** The menu closed: what the last screen showed is now "seen" (progress after it still counts as new). */
    public void remember(Player p, SkillManager sm) {
        UUID id = p.getUniqueId();
        Map<String, Seen> shown = lastShown.remove(id);
        if (shown == null) shown = snapshot(id, sm);
        if (shown == null) return;
        lastClosed.put(id, shown);
        announced.remove(id);
        looked.remove(id);
    }

    private static Map<String, Seen> snapshot(UUID id, SkillManager sm) {
        if (!sm.hasLoadedSkills(id)) return null;
        Map<String, Seen> m = new HashMap<>();
        for (SkillDef d : SkillDefinitions.all()) {
            m.put(d.name(), new Seen(sm.getSkillLevel(id, d.name()), sm.getSkillXP(id, d.name())));
        }
        return m;
    }

    public void visitedCategory(UUID player, SkillCategory category) {
        lastCategory.put(player, category);
    }

    /** The skill category the player last opened this session, or null. */
    public SkillCategory lastCategory(UUID player) {
        return lastCategory.get(player);
    }

    /** The player left: drop everything. */
    public void forget(UUID player) {
        lastClosed.remove(player);
        lastShown.remove(player);
        lastCategory.remove(player);
        announced.remove(player);
        looked.remove(player);
    }
}

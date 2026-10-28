package Manager;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Iron Reprisal (docs/design/COMBAT_IMPROVEMENTS.md §2): a creature that hits a player in melee while the player wears heavy
 * armor is thrown back, harder with Heavy Armor Mastery's level and with each heavy piece worn. No chance roll, no damage.
 * Pure: combat.yml {@code skills.heavy-armor-training.iron-reprisal}, every key with a code default.
 *
 * @param unlockLevel       the Heavy Armor Mastery level it opens at
 * @param strengthAtUnlock  the server's knockback strength with four heavy pieces at {@code unlockLevel} (a plain hit's is 0.4)
 * @param strengthAtMax     ...growing evenly to this at {@code maxLevel}, then staying
 * @param cooldownMs        an attacker is thrown at most this often
 * @param pvp               players, and animals a player owns, are thrown too (off for now, not permanently)
 * @param exempt            entity types never thrown (EntityType names)
 */
public record IronReprisalRules(boolean enabled, int unlockLevel, double strengthAtUnlock, int maxLevel, double strengthAtMax,
                                long cooldownMs, boolean pvp, boolean sound, Set<String> exempt) {

    /** The knockback strength of a plain unenchanted hit, the scale the menus show the push on. */
    public static final double PLAIN_HIT = 0.4;
    /** The most heavy pieces that count: helmet, chestplate, leggings, boots. */
    public static final int PIECES = 4;
    private static final double MAX_STRENGTH = 5.0;

    public static final IronReprisalRules DEFAULTS = new IronReprisalRules(true, 10, 0.4, 100, 1.0, 500L, false, true,
            Set.of("ENDER_DRAGON", "WITHER"));

    public IronReprisalRules {
        exempt = Set.copyOf(exempt);
    }

    /** The push for a hit taken at {@code level} wearing {@code heavyPieces} heavy pieces; 0 when there is none. */
    public double strength(int level, int heavyPieces) {
        if (!enabled || level < unlockLevel || heavyPieces <= 0) return 0.0;
        double t = maxLevel <= unlockLevel ? 1.0 : Math.min(1.0, (double) (level - unlockLevel) / (maxLevel - unlockLevel));
        return (strengthAtUnlock + (strengthAtMax - strengthAtUnlock) * t) * Math.min(PIECES, heavyPieces) / PIECES;
    }

    /** Whether an entity of this type (an EntityType name) may be thrown. */
    public boolean throwable(String entityType) {
        return entityType != null && !exempt.contains(entityType.toUpperCase(Locale.ROOT));
    }

    /** The technique a level-up from {@code from} to {@code to} opens ("Iron Reprisal: ..."), as level-up messages show it. */
    public List<String> openedBetween(int from, int to) {
        if (!enabled || from >= unlockLevel || to < unlockLevel) return List.of();
        return List.of("Iron Reprisal: creatures that hit you in melee while you wear heavy armor are thrown back");
    }

    /** Read {@code s} (combat.yml {@code skills.heavy-armor-training.iron-reprisal}); a missing or bad value keeps the default. */
    public static IronReprisalRules parse(ConfigurationSection s, Consumer<String> problems) {
        IronReprisalRules d = DEFAULTS;
        if (s == null) return d;
        String at = s.getCurrentPath() + ".";
        int unlock = s.getInt("unlock-level", d.unlockLevel());
        if (unlock < 1 || unlock > 1000) { problems.accept(at + "unlock-level must be 1-1000 - using " + d.unlockLevel()); unlock = d.unlockLevel(); }
        int max = s.getInt("max-strength-level", d.maxLevel());
        if (max < unlock || max > 1000) { problems.accept(at + "max-strength-level must be unlock-level-1000 - using " + Math.max(unlock, d.maxLevel())); max = Math.max(unlock, d.maxLevel()); }
        double first = strength(s, "strength-at-unlock", d.strengthAtUnlock(), problems);
        double last = strength(s, "max-strength", d.strengthAtMax(), problems);
        long cooldown = s.getLong("cooldown-ms", d.cooldownMs());
        if (cooldown < 0 || cooldown > 60_000) { problems.accept(at + "cooldown-ms must be 0-60000 - using " + d.cooldownMs()); cooldown = d.cooldownMs(); }
        Set<String> exempt = d.exempt();
        if (s.isList("exempt")) {
            java.util.Set<String> names = new java.util.HashSet<>();
            for (String n : s.getStringList("exempt")) {
                String key = n.trim().toUpperCase(Locale.ROOT);
                if (!key.isEmpty()) names.add(key);
            }
            exempt = names;
        }
        return new IronReprisalRules(s.getBoolean("enabled", d.enabled()), unlock, first, max, last, cooldown,
                s.getBoolean("pvp", d.pvp()), s.getBoolean("sound", d.sound()), exempt);
    }

    private static double strength(ConfigurationSection s, String key, double def, Consumer<String> problems) {
        double v = s.getDouble(key, def);
        if (!Double.isFinite(v) || v < 0 || v > MAX_STRENGTH) {
            problems.accept(s.getCurrentPath() + "." + key + " must be 0-" + MAX_STRENGTH + " - using " + def);
            return def;
        }
        return v;
    }
}

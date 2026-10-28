package Effects;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure text for effects (docs/design/ACTIVE_EFFECTS.md): times left, the head's summary lines, and what each vanilla
 * effect does under Cerberus, which runs its own health and damage.
 */
public final class EffectsText {

    private EffectsText() {}

    /** "8m 42s", "1h 05m", "42s", "0s"; {@link ActiveEffect#NO_END}: "no time limit". Rounded down to whole seconds. */
    public static String remaining(long ms) {
        if (ms == ActiveEffect.NO_END) return "no time limit";
        if (ms <= 0) return "0s";
        long s = ms / 1000, h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return h + "h " + String.format(Locale.ROOT, "%02dm", m);
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }

    /** "Hearty Stew — 8m 42s remaining", "Speed II — until removed": one line of the head's summary. */
    public static String summary(ActiveEffect e, long now) {
        if (!e.timed()) return e.name() + " — " + (e.endsWhen() != null ? e.endsWhen() : "no time limit");
        return e.name() + " — " + remaining(e.remaining(now)) + " remaining";
    }

    /** "I".."X" for an effect level (vanilla's amplifier + 1); larger numbers stay digits. */
    public static String roman(int level) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return level >= 1 && level < r.length ? r[level] : String.valueOf(level);
    }

    /**
     * What a vanilla effect does here, by its key ("speed", "regeneration"; vanilla's registry key) at {@code level}
     * (amplifier + 1). Cerberus cancels vanilla healing for players, keeps its own health pool and maximum, and takes a hit's
     * damage before vanilla's reductions (Listener/VirtualHealthListener): the health effects say so.
     */
    public static List<String> vanilla(String key, int level) {
        int n = Math.max(1, level);
        String k = key == null ? "" : key.toLowerCase(Locale.ROOT);
        return switch (k) {
            case "regeneration" -> List.of("No effect on Cerberus health: vanilla", "healing is off; your health regenerates", "on its own.");
            case "instant_health" -> List.of("No effect on Cerberus health: vanilla", "healing is off.");
            case "absorption" -> List.of("No effect: Cerberus health has no", "absorption hearts.");
            case "health_boost" -> List.of("No effect: your Cerberus maximum health", "comes from Vitality and your realm.");
            case "resistance" -> List.of("No effect: Cerberus applies its own", "defense, before vanilla's reductions.");
            case "strength" -> List.of("Melee hits: +" + (3 * n) + " base damage (on a", "creature, Cerberus's Power and realm", "multiply it).");
            case "weakness" -> List.of("Melee hits: -" + (4 * n) + " base damage (on a", "creature, before Cerberus's multipliers).");
            case "poison", "wither" -> List.of("Hurts your health over time.");
            case "instant_damage" -> List.of("Hurts your health once.");
            case "fire_resistance" -> List.of("No damage from fire and lava.");
            case "speed" -> List.of("Walking speed +" + (20 * n) + "%.");
            case "slowness" -> List.of("Walking speed -" + Math.min(100, 15 * n) + "%.");
            case "haste" -> List.of("Mining speed +" + (20 * n) + "%, attack speed +" + (10 * n) + "%.");
            case "mining_fatigue" -> List.of("Mining and attack speed much slower.");
            case "jump_boost" -> List.of("Jump higher; less fall damage.");
            case "night_vision" -> List.of("See clearly in the dark.");
            case "water_breathing" -> List.of("Breathe underwater.");
            case "invisibility" -> List.of("Invisible (armor and held items still show).");
            case "glowing" -> List.of("Outlined, visible through walls.");
            case "slow_falling" -> List.of("Fall slowly; no fall damage.");
            case "levitation" -> List.of("Float upwards.");
            case "dolphins_grace" -> List.of("Swim much faster.");
            case "conduit_power" -> List.of("Underwater: breathe, see, mine faster.");
            case "luck" -> List.of("Better vanilla loot.");
            case "unluck" -> List.of("Worse vanilla loot.");
            case "hunger" -> List.of("Your food bar drains faster.");
            case "saturation" -> List.of("Refills your food bar.");
            case "nausea" -> List.of("Your view wobbles.");
            case "blindness", "darkness" -> List.of("Your view is limited.");
            case "bad_omen", "raid_omen", "trial_omen" -> List.of("Vanilla omen: a raid or ominous trial", "is coming.");
            case "hero_of_the_village" -> List.of("Villagers give discounts and gifts.");
            case "infested" -> List.of("When hit: a chance to release", "silverfish.");
            case "wind_charged", "weaving", "oozing" -> List.of("Vanilla effect: something happens", "when you die.");
            default -> List.of("Vanilla effect.");
        };
    }

    /** A vanilla effect key ("fire_resistance") as a name ("Fire Resistance"). */
    public static String vanillaName(String key) {
        String n = NAMES.get(key);
        if (n != null) return n;
        StringBuilder sb = new StringBuilder();
        for (String w : (key == null ? "" : key).split("_")) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static final Map<String, String> NAMES = Map.of(
            "instant_health", "Instant Health",
            "instant_damage", "Instant Damage",
            "dolphins_grace", "Dolphin's Grace",
            "unluck", "Bad Luck",
            "hero_of_the_village", "Hero of the Village");
}

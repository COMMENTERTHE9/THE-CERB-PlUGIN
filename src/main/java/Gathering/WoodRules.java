package Gathering;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Woodcutting's techniques (docs/design/WOODCUTTING.md §6, §8), from the {@code woodcutting:} section of
 * resources.yml: how many logs Felling takes by level, giant trunks, the rest between fellings, the canopy, how far a
 * tree reaches, and the heartwood chance. Pure and immutable; unit-tested.
 *
 * @param fellingAt    Woodcutting level → the most logs one felling takes from that level on
 * @param giantAt      the level from which 2×2 trunks can be felled
 * @param restPerLogMs the wait after a felling, per log it took
 * @param restHalvedAt the level from which that wait is halved
 * @param canopyAt     the level from which a felling brings the tree's natural leaves down too
 * @param minLeaves    natural leaves a tree's logs must touch to be felled (a build has none)
 * @param heartwoodItem the custom item heartwood is ({@code items:})
 * @param levelFactor  heartwood chance × (1 + level / this)
 */
public record WoodRules(NavigableMap<Integer, Integer> fellingAt, int giantAt, long restPerLogMs, int restHalvedAt,
                        int canopyAt, TreeScan.Reach reach, int minLeaves, String heartwoodItem, double levelFactor) {

    private static final String[] NUMERALS = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII"};

    public static final WoodRules DEFAULTS = new WoodRules(new TreeMap<>(Map.of(10, 6, 25, 24, 50, 64, 100, 128)), 50, 300, 100,
            75, new TreeScan.Reach(7, 32, 1), 2, "heartwood", 50);

    public WoodRules {
        fellingAt = Collections.unmodifiableNavigableMap(new TreeMap<>(fellingAt));
        restPerLogMs = Math.max(0, restPerLogMs);
        minLeaves = Math.max(1, minLeaves);
        levelFactor = levelFactor > 0 && Double.isFinite(levelFactor) ? levelFactor : 50;
    }

    /** The most logs one felling takes at {@code level} (0: Felling isn't open yet). */
    public int capacity(int level) {
        Map.Entry<Integer, Integer> e = fellingAt.floorEntry(level);
        return e == null ? 0 : Math.max(0, e.getValue());
    }

    /** The level Felling opens at (Integer.MAX_VALUE when the file has none). */
    public int firstFellingLevel() {
        return fellingAt.isEmpty() ? Integer.MAX_VALUE : fellingAt.firstKey();
    }

    /** "Felling II" at the second step reached; null before the first. */
    public String fellingName(int level) {
        int step = fellingAt.headMap(level, true).size();
        if (step == 0) return null;
        return "Felling " + (step <= NUMERALS.length ? NUMERALS[step - 1] : String.valueOf(step));
    }

    public boolean giantTrunks(int level) { return level >= giantAt; }

    public boolean canopy(int level) { return level >= canopyAt; }

    /** How long the next felling waits after one that took {@code logs} logs. */
    public long restMs(int level, int logs) {
        long ms = restPerLogMs * Math.max(0, logs);
        return level >= restHalvedAt ? ms / 2 : ms;
    }

    /** The chance of heartwood from one natural log of a wood whose own chance is {@code base}, at {@code level}. */
    public double heartwoodChance(double base, int level) {
        if (!(base > 0) || !Double.isFinite(base)) return 0;
        return Math.min(1.0, base * (1.0 + Math.max(0, level) / levelFactor));
    }

    /**
     * The techniques levels {@code from} → {@code to} opened, for the level-up: "Felling I: Sneak while cutting a natural
     * tree to bring down up to 6 logs", "Felling III: up to 64 logs, and giant 2×2 trunks", "Canopy: ...".
     */
    public List<String> openedBetween(int from, int to) {
        List<String> out = new ArrayList<>();
        if (to <= from) return out;
        for (Map.Entry<Integer, Integer> e : fellingAt.subMap(from, false, to, true).entrySet()) {
            int k = e.getKey();
            String what = capacity(k - 1) == 0 ? "Sneak while cutting a natural tree to bring down up to " + e.getValue() + " logs"
                    : "Felling takes up to " + e.getValue() + " logs";
            if (k == giantAt) what += ", and giant 2×2 trunks";
            if (k == restHalvedAt) what += "; half the rest between fellings";
            out.add(fellingName(k) + ": " + what);
        }
        if (giantAt > from && giantAt <= to && !fellingAt.containsKey(giantAt)) out.add("Felling: giant 2×2 trunks");
        if (canopyAt > from && canopyAt <= to) out.add("Canopy: a felled tree's natural leaves come down with it (their own drops)");
        if (restHalvedAt > from && restHalvedAt <= to && !fellingAt.containsKey(restHalvedAt)) out.add("Felling: half the rest between fellings");
        return out;
    }
}

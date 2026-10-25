package Gathering;

import java.util.Locale;

/**
 * The gathering rules that need no server (docs/design/GATHERING.md), unit-tested: XP for a
 * resource at a skill level, whether a tool can gather a resource, the Fortune bonus chance, and
 * what each extraction method earns.
 */
public final class GatherRules {

    private GatherRules() {}

    /** How a block came to be extracted, and what that earns. */
    public enum Method {
        /** A player broke it in survival or adventure: XP and the Fortune bonus. */
        MANUAL(true, true),
        /** A player in creative mode: nothing. */
        CREATIVE(false, false),
        /** TNT, a creeper, a bed: the drops only. */
        EXPLOSION(false, false),
        /** A future drill or machine, through the same definitions: the drops only, for now. */
        AUTOMATED(false, false);

        public final boolean xp;
        public final boolean bonus;

        Method(boolean xp, boolean bonus) { this.xp = xp; this.bonus = bonus; }
    }

    /**
     * XP for gathering a resource worth {@code xp} at {@code level}, by a player at
     * {@code skillLevel}: a weak resource (the player {@code gap} or more levels above it) pays
     * {@code factor} of it, as a weak mob's kill does.
     */
    public static double xp(double xp, int level, int skillLevel, int gap, double factor) {
        double base = Double.isFinite(xp) ? Math.max(0, xp) : 0;
        if (gap > 0 && skillLevel - Math.max(1, level) >= gap) base *= Math.max(0, Math.min(1, factor));
        return base;
    }

    /**
     * True when a tool of {@code kinds} and {@code tier} can gather a resource needing
     * {@code kind} at {@code minTier} (a null kind: any tool, or none).
     */
    public static boolean toolFits(String kind, int minTier, java.util.Set<String> kinds, int tier) {
        if (kind == null || kind.isEmpty()) return true;
        return kinds != null && kinds.contains(kind.toLowerCase(Locale.ROOT)) && tier >= minTier;
    }

    /** The chance of one extra drop at {@code fortune} points: {@code perPoint} each, at most {@code max} (and never above 1). */
    public static double bonusChance(double fortune, double perPoint, double max) {
        if (!(fortune > 0) || !(perPoint > 0) || !Double.isFinite(fortune) || !Double.isFinite(perPoint)) return 0;
        return Math.max(0, Math.min(Math.min(1.0, Double.isFinite(max) ? max : 0), fortune * perPoint));
    }

    /**
     * A vanilla tool's kinds and tier from its material name ("IRON_PICKAXE" → pickaxe, 2), or
     * null when the material isn't one. Tiers: wood and gold 0, stone and copper 1, iron 2,
     * diamond 3, netherite 4.
     */
    public static Tool vanillaTool(String material) {
        if (material == null) return null;
        String m = material.toUpperCase(Locale.ROOT);
        String kind = m.endsWith("_PICKAXE") ? "pickaxe" : m.endsWith("_SHOVEL") ? "shovel" : m.endsWith("_HOE") ? "hoe"
                : m.endsWith("_AXE") ? "axe" : null;
        if (kind == null) return null;
        int tier;
        if (m.startsWith("WOODEN_") || m.startsWith("GOLDEN_")) tier = 0;
        else if (m.startsWith("STONE_") || m.startsWith("COPPER_")) tier = 1;
        else if (m.startsWith("IRON_")) tier = 2;
        else if (m.startsWith("DIAMOND_")) tier = 3;
        else if (m.startsWith("NETHERITE_")) tier = 4;
        else return null;
        return new Tool(m.toLowerCase(Locale.ROOT), java.util.Set.of(kind), tier);
    }

    /** A tool as the rules see it: its id (a material or a Cerberus tool id), what it is, and how good. */
    public record Tool(String id, java.util.Set<String> kinds, int tier) {}

    /**
     * Fractions of XP kept per player and skill, so a 0.25-XP block is never lost to rounding
     * (skill XP is awarded in whole points). Not thread-safe: one per player, on their thread.
     */
    public static final class Carry {
        private final java.util.Map<String, Double> rest = new java.util.HashMap<>();

        /** Add {@code xp} for {@code skill}; returns the whole points to award now, keeping the remainder. */
        public long add(String skill, double xp) {
            if (skill == null || !(xp > 0) || !Double.isFinite(xp)) return 0;
            double total = rest.getOrDefault(skill, 0.0) + xp;
            long whole = (long) Math.floor(total + 1e-9);
            rest.put(skill, Math.max(0, total - whole));
            return whole;
        }

        /** The fraction kept for {@code skill}. */
        public double pending(String skill) { return rest.getOrDefault(skill, 0.0); }
    }
}

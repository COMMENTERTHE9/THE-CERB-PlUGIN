package Gathering;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Alchemy's rules (docs/design/ALCHEMY.md), parsed from {@code resources.yml alchemy:}: what a completed vanilla brew pays,
 * and the Cerberus brews: an Awkward Potion and a reagent (a herb, or a rare reagent from Herbalism) brewed into a draught
 * (healing on the Cerberus health pool) or an elixir (a timed effect through the attribute engine). A brew's tier is the
 * alchemist's level when it starts (Common, Fine, Superior, Masterwork: {@link Quality}), one higher with a quality reagent
 * or the brew's lifting rare reagent. Pure: no server calls.
 */
public final class AlchemyRules {

    /** A draught heals over a few seconds and is gone; an elixir is a timed effect, one at a time. */
    public enum Kind {
        DRAUGHT, ELIXIR;

        public String label() { return this == DRAUGHT ? "Draught" : "Elixir"; }
    }

    /**
     * One brew. {@code reagent}: the vanilla material it is brewed from, or {@code rare}: the rare reagent id (one of the
     * two is set). {@code lift}: a rare reagent id that may stand in for the reagent and lifts the tier, or null. Effects,
     * restoration and time at the Common tier, as meals have them ({@link CookingRules.Effect}, {@link CookingRules.Restore}).
     */
    public record Brew(String id, String name, Kind kind, int level, double xp, Material reagent, String rare, String lift, int color,
                       int minutes, List<CookingRules.Effect> effects, CookingRules.Restore restore) {

        /** True when {@code material} (a plain item, no Cerberus id) is this brew's reagent. */
        public boolean takes(Material material) { return reagent != null && reagent == material; }
    }

    /** Vanilla brewing's own ingredients (1.21): a brew never takes one, so vanilla brews keep working. */
    public static final Set<Material> VANILLA_INGREDIENTS = java.util.EnumSet.of(Material.NETHER_WART, Material.GLOWSTONE_DUST,
            Material.REDSTONE, Material.FERMENTED_SPIDER_EYE, Material.GUNPOWDER, Material.DRAGON_BREATH, Material.SUGAR,
            Material.RABBIT_FOOT, Material.GLISTERING_MELON_SLICE, Material.SPIDER_EYE, Material.PUFFERFISH, Material.MAGMA_CREAM,
            Material.GOLDEN_CARROT, Material.BLAZE_POWDER, Material.GHAST_TEAR, Material.TURTLE_HELMET, Material.TURTLE_SCUTE,
            Material.PHANTOM_MEMBRANE, Material.BREEZE_ROD, Material.SLIME_BLOCK, Material.STONE, Material.COBWEB);

    private final String skill;
    private final double vanillaXp;
    private final int[] tierAt;
    private final double[] potency, duration;
    private final Map<String, Brew> brews;

    public static final AlchemyRules NONE = new AlchemyRules(null, 0, new int[] {25, 50, 75}, new double[] {1, 1.2, 1.4, 1.6},
            new double[] {1, 1.25, 1.5, 2}, Map.of());

    AlchemyRules(String skill, double vanillaXp, int[] tierAt, double[] potency, double[] duration, Map<String, Brew> brews) {
        this.skill = skill;
        this.vanillaXp = vanillaXp;
        this.tierAt = tierAt;
        this.potency = potency;
        this.duration = duration;
        this.brews = brews;
    }

    public boolean enabled() { return skill != null; }

    public String skill() { return skill; }

    /** XP per bottle of a vanilla brew completed (0: vanilla brewing pays nothing). */
    public double vanillaXp() { return vanillaXp; }

    public Collection<Brew> brews() { return brews.values(); }

    public Brew brew(String id) { return id == null ? null : brews.get(id); }

    /** The brew whose reagent is the plain item {@code m}, or null. */
    public Brew byReagent(Material m) {
        if (m == null) return null;
        for (Brew b : brews.values()) if (b.takes(m)) return b;
        return null;
    }

    /** The brew a rare reagent {@code rareId} makes: its own reagent, or the reagent it lifts. Null when none. */
    public Brew byRare(String rareId) {
        if (rareId == null) return null;
        for (Brew b : brews.values()) if (rareId.equals(b.rare())) return b;
        for (Brew b : brews.values()) if (rareId.equals(b.lift())) return b;
        return null;
    }

    /** The tier an alchemist at {@code level} brews at, before the reagent. */
    public Quality tierAt(int level) {
        Quality q = Quality.COMMON;
        for (int i = 0; i < tierAt.length; i++) if (level >= tierAt[i]) q = Quality.values()[i + 1];
        return q;
    }

    /** The level {@code q} starts at (0 for Common). */
    public int levelFor(Quality q) {
        return q == Quality.COMMON ? 0 : tierAt[q.ordinal() - 1];
    }

    /**
     * A brew's tier: the alchemist's, one higher (Masterwork at most) when the reagent lifts it: a reagent of Fine quality
     * or better ({@code reagentQuality}), or the brew's lifting rare reagent ({@code lifted}). At most one tier up.
     */
    public Quality tier(int level, Quality reagentQuality, boolean lifted) {
        Quality q = tierAt(level);
        boolean up = lifted || (reagentQuality != null && reagentQuality.ordinal() >= Quality.FINE.ordinal());
        if (up && q != Quality.MASTERWORK) q = Quality.values()[q.ordinal() + 1];
        return q;
    }

    /** Every number of a brew's effect (and a draught's healing share) at tier {@code q} is multiplied by this. */
    public double potency(Quality q) { return potency[q.ordinal()]; }

    /** How long an elixir at tier {@code q} lasts, in milliseconds (0 for a draught). */
    public long durationMs(Brew b, Quality q) {
        return b.kind() == Kind.DRAUGHT ? 0 : Math.round(b.minutes() * 60_000L * duration[q.ordinal()]);
    }

    public double durationFactor(Quality q) { return duration[q.ordinal()]; }

    /** A draught's healing at tier {@code q}: the share of max health over its seconds (the share at most 1). */
    public double restoreShare(Brew b, Quality q) {
        return b.restore() == null ? 0 : Math.min(1, b.restore().share() * potency(q));
    }

    /** Every vanilla material a brew takes as its reagent (the brewing stand's quick check). */
    public Set<Material> reagents() {
        Set<Material> out = java.util.EnumSet.noneOf(Material.class);
        for (Brew b : brews.values()) if (b.reagent() != null) out.add(b.reagent());
        return out;
    }

    /** The brews {@code from} → {@code to} opened, as level-ups show them ("Draught of Mending: ..."). */
    public List<String> openedBetween(int from, int to) {
        List<String> out = new ArrayList<>();
        for (Brew b : brews.values()) {
            if (b.level() > from && b.level() <= to) out.add(b.name() + ": brew it from an Awkward Potion and " + reagentName(b));
        }
        for (Quality q : List.of(Quality.FINE, Quality.SUPERIOR, Quality.MASTERWORK)) {
            int at = levelFor(q);
            if (at > from && at <= to) out.add(q.displayName + " brews: every brew's numbers x" + CookingRules.trim(potency(q))
                    + ", elixirs last x" + CookingRules.trim(durationFactor(q)));
        }
        return out;
    }

    /** "a poppy", or the rare reagent's id as a name when it has no item name here. */
    static String reagentName(Brew b) {
        if (b.reagent() != null) return "a " + b.reagent().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return FarmingRules.label(b.rare()) + " (a rare reagent)";
    }

    // ── loading ──────────────────────────────────────────────────

    /**
     * {@code rares}: the rare reagents Herbalism knows (their ids), so a brew can name one as its reagent or its lift.
     * {@code taken}: vanilla brewing's own ingredients: a brew never claims one, so vanilla brews keep working.
     */
    static AlchemyRules from(ConfigurationSection s, java.util.function.UnaryOperator<String> skills, Set<String> rares, Set<Material> taken,
                             List<String> problems) {
        if (s == null) return NONE;
        String written = s.getString("skill", "Alchemy");
        String skill = skills.apply(written.trim());
        if (skill == null) { problems.add("alchemy: unknown skill " + written + " - alchemy pays nothing"); return NONE; }
        double vanillaXp = s.getDouble("vanilla-xp", 2);
        if (!(vanillaXp >= 0 && vanillaXp <= 10_000)) { problems.add("alchemy vanilla-xp " + vanillaXp + " outside 0-10000 - using 2"); vanillaXp = 2; }
        int[] tierAt = {s.getInt("tiers.fine", 25), s.getInt("tiers.superior", 50), s.getInt("tiers.masterwork", 75)};
        if (!(tierAt[0] >= 1 && tierAt[0] <= tierAt[1] && tierAt[1] <= tierAt[2] && tierAt[2] <= 1000)) {
            problems.add("alchemy tiers: " + tierAt[0] + "/" + tierAt[1] + "/" + tierAt[2] + " must rise (1-1000) - using 25/50/75");
            tierAt = new int[] {25, 50, 75};
        }
        double[] potency = CookingRules.factors(s.getConfigurationSection("potency"), new double[] {1, 1.2, 1.4, 1.6}, 0.1, 5, "alchemy potency", problems);
        double[] duration = CookingRules.factors(s.getConfigurationSection("duration"), new double[] {1, 1.25, 1.5, 2}, 0.1, 10, "alchemy duration", problems);

        Map<String, Brew> brews = new LinkedHashMap<>();
        Map<Material, String> byReagent = new java.util.EnumMap<>(Material.class);
        Map<String, String> byRare = new LinkedHashMap<>();
        ConfigurationSection bs = s.getConfigurationSection("brews");
        if (bs != null) for (String id : bs.getKeys(false)) {
            Object raw = bs.get(id);
            if (Boolean.FALSE.equals(raw) || "off".equalsIgnoreCase(String.valueOf(raw))) continue;
            ConfigurationSection b = bs.getConfigurationSection(id);
            if (b == null) { problems.add("alchemy brew " + id + ": not a section"); continue; }
            List<String> why = new ArrayList<>();
            if (!FarmingRules.ID.matcher(id).matches()) why.add("id must be plain (a-z, 0-9, _)");
            Kind kind = "draught".equalsIgnoreCase(b.getString("kind", "elixir")) ? Kind.DRAUGHT
                    : "elixir".equalsIgnoreCase(b.getString("kind", "elixir")) ? Kind.ELIXIR : null;
            if (kind == null) why.add("kind " + b.getString("kind") + " (draught or elixir)");
            int level = b.getInt("level", 1);
            if (level < 1 || level > 1000) why.add("level " + level + " outside 1-1000");
            double xp = b.getDouble("xp", 0);
            if (!(xp >= 0 && xp <= 100_000)) why.add("xp " + xp + " outside 0-100000");
            // The reagent: a vanilla material, or a rare reagent's id (lower case, as rare-reagents: names it).
            String written2 = b.getString("reagent", "").trim();
            Material reagent = null;
            String rare = null;
            if (rares.contains(written2)) rare = written2;
            else {
                reagent = GatheringCatalog.material(written2);
                if (reagent == null || reagent.isLegacy() || reagent == Material.AIR) why.add("unknown reagent " + written2 + " (a material or a rare reagent)");
                else if (taken.contains(reagent)) why.add(reagent + " is one of vanilla brewing's own ingredients");
                else if (byReagent.containsKey(reagent)) why.add(reagent + " is already brew " + byReagent.get(reagent) + "'s reagent");
            }
            if (rare != null && byRare.containsKey(rare)) why.add(rare + " already makes brew " + byRare.get(rare));
            // A lift is extra: one that can't be used (Herbalism off, the rare reagent turned off or another brew's) is left out
            // and the brew stays, made from its own reagent.
            String lift = b.getString("lift");
            if (lift != null) {
                lift = lift.trim();
                String noLift = !rares.contains(lift) ? "unknown lift " + lift + " (a rare reagent)"
                        : byRare.containsKey(lift) ? "lift " + lift + " already makes brew " + byRare.get(lift) : null;
                if (noLift != null) {
                    problems.add("alchemy brew " + id + ": " + noLift + " - the brew stays, without its lift");
                    lift = null;
                }
            }
            int color = parseColor(b.getString("color", "#7E57C2"));
            if (color < 0) why.add("color " + b.getString("color") + " (#RRGGBB)");
            int minutes = b.getInt("minutes", kind == Kind.DRAUGHT ? 0 : 8);
            if (kind == Kind.ELIXIR && (minutes < 1 || minutes > 240)) why.add("minutes " + minutes + " outside 1-240");
            List<CookingRules.Effect> effects = CookingRules.effects(b.getMapList("effects"), why);
            CookingRules.Restore restore = null;
            if (b.isConfigurationSection("restore")) {
                double share = b.getDouble("restore.share", 0);
                int seconds = b.getInt("restore.seconds", 0);
                if (!(share > 0 && share <= 1) || seconds < 1 || seconds > 60) why.add("restore " + share + " over " + seconds + " s (share (0, 1], 1-60 s)");
                else restore = new CookingRules.Restore(share, seconds);
            }
            if (kind == Kind.DRAUGHT && (restore == null || !effects.isEmpty())) why.add("a draught heals (restore:) and has no effects");
            if (kind == Kind.ELIXIR && (effects.isEmpty() || restore != null)) why.add("an elixir has effects and no restore:");
            if (!why.isEmpty()) { problems.add("alchemy brew " + id + ": " + String.join("; ", why)); continue; }
            Brew brew = new Brew(id, b.getString("name", FarmingRules.label(id)), kind, level, xp, reagent, rare, lift, color,
                    kind == Kind.DRAUGHT ? 0 : minutes, List.copyOf(effects), restore);
            brews.put(id, brew);
            if (reagent != null) byReagent.put(reagent, id);
            if (rare != null) byRare.put(rare, id);
            if (lift != null) byRare.put(lift, id);
        }
        return new AlchemyRules(skill, vanillaXp, tierAt, potency, duration, Collections.unmodifiableMap(brews));
    }

    /** "#C0392B" → 0xC0392B; -1 when not a colour. */
    static int parseColor(String s) {
        if (s == null) return -1;
        String t = s.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (t.length() != 6) return -1;
        try { return Integer.parseInt(t, 16); } catch (NumberFormatException e) { return -1; }
    }
}

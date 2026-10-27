package Gathering;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Everything gathering and forging read, from {@code resources.yml} (docs/design/GATHERING.md):
 * resources by block, custom tools and items, smelting and forging XP, quality odds and effects,
 * and the Fortune rule. Built once per load and immutable; lookups are enum maps. A bad entry is
 * refused on its own with a reason in {@link #problems()}, never the whole file.
 */
public final class GatheringCatalog {

    /**
     * A resource: what gathers it, what it is worth, and (for a custom one) what it drops.
     *
     * @param fortuneYield which Fortune its bonus counts (Mining's ore, Woodcutting's wood); null = the general part
     * @param tree         a wood's tree (docs/design/WOODCUTTING.md): its leaves, heartwood, discovery; null for others
     */
    public record ResourceDef(String id, String skill, Set<Material> blocks, String tool, int tier, int level,
                              double xp, boolean fortune, List<Drop> drops, Attributes.Yield fortuneYield, TreeDef tree) {
        public ResourceDef(String id, String skill, Set<Material> blocks, String tool, int tier, int level, double xp, boolean fortune,
                           List<Drop> drops) {
            this(id, skill, blocks, tool, tier, level, xp, fortune, drops, null, null);
        }

        /** True when its drops replace the block's vanilla drops (a custom resource). */
        public boolean replacesDrops() { return !drops.isEmpty(); }

        /** True for a wood: its blocks are logs of a tree. */
        public boolean isTree() { return tree != null; }

        /** "Oak", "Pale oak": the id as players read it. */
        public String displayName() {
            String t = id.replace('_', ' ');
            return t.isEmpty() ? t : Character.toUpperCase(t.charAt(0)) + t.substring(1);
        }
    }

    /**
     * A wood's tree (docs/design/WOODCUTTING.md §4, §5): the leaves its natural trees grow (Felling needs some), the
     * chance of heartwood per natural log at level 0, and the XP its first natural log pays as a discovery.
     */
    /**
     * A wood's natural tree: its leaves, the heartwood chance per log, the discovery XP, and how wide its trunk can be
     * at the base (2: single and 2×2 trunks; the Nether's huge fungi grow some 3×3 stems).
     */
    public record TreeDef(Set<Material> leaves, double heartwood, double discoveryXp, int trunk) {
        public TreeDef(Set<Material> leaves, double heartwood, double discoveryXp) { this(leaves, heartwood, discoveryXp, 2); }
    }

    /**
     * An upgrade a custom item makes to a tool or weapon (docs/design/WOODCUTTING.md §8): {@code item} in a crafting
     * grid with one of {@code on} (item kinds: axe, bow...; "for:" in the file) adds {@code amount} of {@code stat}
     * while it is held. Once per item.
     */
    public record UpgradeDef(String id, String item, Set<String> on, Attributes.Stat stat, double amount, String name) {}

    /** Item kinds an upgrade can go on (GearItems.kind). */
    static final Set<String> ITEM_KINDS = Set.of("axe", "pickaxe", "shovel", "hoe", "sword", "spear", "bow", "crossbow", "trident", "mace", "rod");

    /** A drop: a Cerberus item id or a material, {@code min} to {@code max} of it. */
    public record Drop(String item, int min, int max) {}

    /** A custom tool: an item carrying this id, gathering what its kinds allow up to its tier. */
    public record ToolDef(String id, Material material, String name, Set<String> kinds, int tier) {}

    /**
     * A custom item (a custom resource's drop, a fish species, a creature's drop). {@code vanilla}: it behaves as its material
     * in vanilla uses (a species is eaten and cooked like any fish); otherwise it is never placed, burnt or used in a vanilla
     * recipe (Upgrades).
     */
    public record ItemDef(String id, Material material, String name, List<String> lore, boolean vanilla) {
        public ItemDef(String id, Material material, String name, List<String> lore) { this(id, material, name, lore, false); }
    }

    /** What forged gear's quality adds, by quality (index = ordinal; Common adds nothing). */
    public record QualityRules(String skill, double[] oddsAt1, double[] oddsAt100, double[] weaponPower,
                               double[] armorFortitude, double[] toolFortune) {}

    /** The kinds of gear forging knows, from the item's material name. */
    public enum Gear { WEAPON, TOOL, WEAPON_AND_TOOL, ARMOR }

    private static final Map<String, Integer> UNITS = Map.of("SWORD", 2, "AXE", 3, "PICKAXE", 3, "SHOVEL", 1, "HOE", 2,
            "SPEAR", 1, "HELMET", 5, "CHESTPLATE", 8, "LEGGINGS", 7, "BOOTS", 4);

    /** The tool kinds vanilla tools have ({@link GatherRules#vanillaTool}); a resource may also name a custom tool's kind. */
    static final Set<String> VANILLA_KINDS = Set.of("pickaxe", "axe", "shovel", "hoe");

    private final Map<Material, ResourceDef> byBlock;
    private final Map<String, ResourceDef> resources;
    private final Map<String, ToolDef> tools;
    private final Map<String, ItemDef> items;
    private final String smeltingSkill;
    private final Map<Material, Double> smelting;
    private final String forgingSkill;
    private final Map<String, Double> perUnit;
    private final Map<Material, Double> forgeOverrides;
    private final double netheriteUpgrade;
    private final Set<String> needsSmelted;
    private final QualityRules quality;
    private final double fortunePerPoint, fortuneMax;
    private final int weakGap;
    private final double weakFactor;
    private final WoodRules wood;
    private final double weakToolFactor;
    private final Map<String, UpgradeDef> upgrades;
    private final FishingRules fishing;
    private final TackleRules tackle;
    private final List<String> problems;

    private GatheringCatalog(Map<Material, ResourceDef> byBlock, Map<String, ResourceDef> resources, Map<String, ToolDef> tools,
                             Map<String, ItemDef> items, String smeltingSkill, Map<Material, Double> smelting, String forgingSkill,
                             Map<String, Double> perUnit, Map<Material, Double> forgeOverrides, double netheriteUpgrade,
                             Set<String> needsSmelted, QualityRules quality, double fortunePerPoint, double fortuneMax, int weakGap,
                             double weakFactor, WoodRules wood, double weakToolFactor, Map<String, UpgradeDef> upgrades,
                             FishingRules fishing, TackleRules tackle, List<String> problems) {
        this.byBlock = byBlock;
        this.resources = resources;
        this.tools = tools;
        this.items = items;
        this.smeltingSkill = smeltingSkill;
        this.smelting = smelting;
        this.forgingSkill = forgingSkill;
        this.perUnit = perUnit;
        this.forgeOverrides = forgeOverrides;
        this.netheriteUpgrade = netheriteUpgrade;
        this.needsSmelted = needsSmelted;
        this.quality = quality;
        this.fortunePerPoint = fortunePerPoint;
        this.fortuneMax = fortuneMax;
        this.weakGap = weakGap;
        this.weakFactor = weakFactor;
        this.wood = wood;
        this.weakToolFactor = weakToolFactor;
        this.upgrades = upgrades;
        this.fishing = fishing;
        this.tackle = tackle;
        this.problems = List.copyOf(problems);
    }

    public static GatheringCatalog empty() { return from(null); }

    private static volatile GatheringCatalog current = empty();

    /** The catalog in use (menus read it for their numbers); empty until the plugin loads resources.yml. */
    public static GatheringCatalog current() { return current; }

    /** Put {@code c} in use. */
    public static void setCurrent(GatheringCatalog c) { current = c != null ? c : empty(); }

    /** The resource a block of {@code m} is, or null. */
    public ResourceDef resource(Material m) { return m == null ? null : byBlock.get(m); }
    public Map<String, ResourceDef> resources() { return resources; }
    public ToolDef tool(String id) { return id == null ? null : tools.get(id); }
    public Map<String, ToolDef> tools() { return tools; }
    public ItemDef item(String id) { return id == null ? null : items.get(id); }
    public Map<String, ItemDef> items() { return items; }
    /** True when placing a block of {@code m} must be remembered (it is some resource's block). */
    public boolean tracked(Material m) { return m != null && byBlock.containsKey(m); }

    public String smeltingSkill() { return smeltingSkill; }
    /** XP for taking one {@code m} out of a furnace (0 when none). */
    public double smeltXp(Material m) { return m == null ? 0 : smelting.getOrDefault(m, 0.0); }

    public String forgingSkill() { return forgingSkill; }
    public double netheriteUpgradeXp() { return netheriteUpgrade; }

    /**
     * Metals that pay forging XP and roll a quality only as far as the player smelted them
     * ({@code forging.needs-smelted}): a farm makes iron, gold and copper without any mining.
     */
    public Set<String> needsSmelted() { return needsSmelted; }

    /** True when forging {@code gear} needs that much smelted metal first. */
    public boolean needsSmelted(Material gear) {
        String metal = gear == null ? null : metalOf(gear.name());
        return metal != null && needsSmelted.contains(metal);
    }
    public QualityRules quality() { return quality; }
    public double fortunePerPoint() { return fortunePerPoint; }
    public double fortuneMax() { return fortuneMax; }
    public int weakGap() { return weakGap; }
    public double weakFactor() { return weakFactor; }
    /** Woodcutting's techniques (the {@code woodcutting:} section). */
    public WoodRules wood() { return wood; }
    /** What a wood pays with too weak an axe (a soft gate): this share of its XP, and no Felling, bonus or heartwood. */
    public double weakToolFactor() { return weakToolFactor; }
    public Map<String, UpgradeDef> upgrades() { return upgrades; }
    public UpgradeDef upgrade(String id) { return id == null ? null : upgrades.get(id); }
    /** Fishing's rules (docs/design/FISHING.md); {@link FishingRules#NONE} without a fishing section. */
    public FishingRules fishing() { return fishing; }
    /** Fishing equipment's rules (docs/design/FISHING_EQUIPMENT.md); {@link TackleRules#NONE} without a tackle section. */
    public TackleRules tackle() { return tackle; }
    public List<String> problems() { return problems; }

    /** The skill the woods train (Woodcutting): its levels open the techniques; null when no wood is defined. */
    public String woodSkill() {
        for (ResourceDef r : resources.values()) if (r.isTree()) return r.skill();
        return null;
    }

    /** The woods (resources with a tree), in file order. */
    public List<ResourceDef> woods() {
        List<ResourceDef> out = new ArrayList<>();
        for (ResourceDef r : resources.values()) if (r.isTree()) out.add(r);
        return out;
    }

    /** XP for forging one item of {@code m} (0 when it isn't forged gear). */
    public double forgeXp(Material m) {
        if (m == null) return 0;
        Double o = forgeOverrides.get(m);
        if (o != null) return o;
        return forgeXp(m.name(), perUnit);
    }

    /** Forging XP from a material name: per-unit XP of its metal × how many units its shape takes. */
    static double forgeXp(String material, Map<String, Double> perUnit) {
        int cut = material.indexOf('_');
        if (cut < 0) return 0;
        String metal = material.substring(0, cut), shape = material.substring(cut + 1);
        if (metal.equals("GOLDEN")) metal = "GOLD";
        Integer units = UNITS.get(shape);
        Double each = perUnit.get(metal.toLowerCase(Locale.ROOT));
        return units == null || each == null ? 0 : units * each;
    }

    /** The metal a gear material is forged from ("IRON_SWORD" → iron, "GOLDEN_HELMET" → gold), or null. */
    public static String metalOf(String material) {
        if (material == null) return null;
        int cut = material.indexOf('_');
        if (cut < 0 || !UNITS.containsKey(material.substring(cut + 1).toUpperCase(Locale.ROOT))) return null;
        String metal = material.substring(0, cut).toUpperCase(Locale.ROOT);
        return (metal.equals("GOLDEN") ? "GOLD" : metal).toLowerCase(Locale.ROOT);
    }

    /** Units of metal a gear material's shape takes (a sword 2, a chestplate 8), 0 when it is no forged gear. */
    public static int unitsOf(String material) {
        if (material == null) return 0;
        int cut = material.indexOf('_');
        Integer u = cut < 0 ? null : UNITS.get(material.substring(cut + 1).toUpperCase(Locale.ROOT));
        return u == null ? 0 : u;
    }

    /** The metal one smelted item is a unit of ("IRON_INGOT" → iron), or null (food, scrap, glass...). */
    public static String smeltedMetal(String material) {
        if (material == null || !material.toUpperCase(Locale.ROOT).endsWith("_INGOT")) return null;
        return material.substring(0, material.length() - "_INGOT".length()).toLowerCase(Locale.ROOT);
    }

    /** What kind of gear an item of {@code material} is, or null when it is none. */
    public static Gear gear(String material) {
        if (material == null) return null;
        String m = material.toUpperCase(Locale.ROOT);
        if (m.endsWith("_SWORD") || m.endsWith("_SPEAR")) return Gear.WEAPON;
        if (m.endsWith("_PICKAXE") || m.endsWith("_SHOVEL") || m.endsWith("_HOE")) return Gear.TOOL;
        if (m.endsWith("_AXE")) return Gear.WEAPON_AND_TOOL;
        if (m.endsWith("_HELMET") || m.endsWith("_CHESTPLATE") || m.endsWith("_LEGGINGS") || m.endsWith("_BOOTS")) return Gear.ARMOR;
        return null;
    }

    /**
     * "How to train" lines for the skill pages, from these definitions (so the numbers players read
     * are the ones in use), by skill.
     */
    public Map<String, List<String>> trainingLines() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        Map<String, List<ResourceDef>> bySkill = new LinkedHashMap<>();
        for (ResourceDef r : resources.values()) bySkill.computeIfAbsent(r.skill(), k -> new ArrayList<>()).add(r);
        bySkill.forEach((skill, list) -> {
            List<String> parts = new ArrayList<>(), axes = new ArrayList<>();
            boolean trees = true;
            for (ResourceDef r : list) {
                parts.add(r.id().replace('_', ' ') + " " + number(r.xp()));
                trees &= r.isTree();
                if (r.isTree() && r.tier() > 0) axes.add(r.id().replace('_', ' ') + ": " + tierName(r.tier()));
            }
            List<String> lines = new ArrayList<>();
            if (trees) {
                lines.add("Cut the logs of trees with an axe in survival: " + String.join(", ", parts) + " XP each.");
                if (!axes.isEmpty()) {
                    lines.add("Harder woods want a better axe for full XP (" + String.join(", ", axes) + "); a weaker one pays "
                            + Math.round(weakToolFactor * 100) + "%.");
                }
            } else {
                lines.add("Break natural blocks by hand in survival: " + String.join(", ", parts) + " XP.");
            }
            if (weakGap > 0) {
                lines.add("Each block has a level (stone 1, deeper ores more); " + weakGap + "+ levels above it you get "
                        + Math.round(weakFactor * 100) + "% of its XP.");
            }
            if (trees) {
                lines.add("Trees grown from saplings pay " + Math.round(wood.grown().xp() * 100) + "% (with bone meal "
                        + Math.round(wood.boneMeal().xp() * 100) + "%); logs players place give nothing. The first log of each wood is a discovery.");
            }
            else lines.add("Blocks placed by players, made by lava and water, or moved by pistons give nothing. Silk Touch keeps the XP.");
            out.put(skill, lines);
        });
        if (!smelting.isEmpty()) {
            List<String> parts = new ArrayList<>();
            smelting.forEach((m, xp) -> parts.add(m.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " " + number(xp)));
            out.computeIfAbsent(smeltingSkill, k -> new ArrayList<>())
                    .add("Take metal out of a furnace or blast furnace: " + String.join(", ", parts) + " XP each.");
        }
        if (!perUnit.isEmpty()) {
            List<String> parts = new ArrayList<>();
            perUnit.forEach((metal, xp) -> parts.add(metal + " " + number(xp)));
            out.computeIfAbsent(forgingSkill, k -> new ArrayList<>()).add("Craft tools, weapons and armor: per unit of metal (a sword takes 2, "
                    + "a chestplate 8): " + String.join(", ", parts) + " XP. Storage blocks and other reversible recipes give nothing.");
            if (!needsSmelted.isEmpty()) {
                out.get(forgingSkill).add("Gear of " + String.join(", ", needsSmelted) + " pays, and gets a quality, only from metal you "
                        + "smelted yourself: each ingot you take out of a furnace covers one unit.");
            }
        }
        if (netheriteUpgrade > 0) {
            out.computeIfAbsent(forgingSkill, k -> new ArrayList<>())
                    .add("Upgrade gear to netherite at a smithing table: " + number(netheriteUpgrade) + " XP.");
        }
        if (fishing.enabled()) {
            FishingRules.Xp x = fishing.xp();
            List<String> lines = out.computeIfAbsent(fishing.skill(), k -> new ArrayList<>());
            lines.add("Fish in survival, by hand or with a farm: the same XP for every catch. Junk " + number(x.junk()) + ", a fish "
                    + number(x.fish()) + ", treasure " + number(x.treasure()) + "; a species of the water " + number(x.of(FishingRules.Rarity.COMMON))
                    + " (common) to " + number(x.of(FishingRules.Rarity.LEGENDARY)) + " (legendary) XP.");
            lines.add("Each water has its own species (" + fishing.waters().size() + " waters, " + fishing.allSpecies().size()
                    + " species); time and weather change what bites. The first of each species is a discovery.");
            lines.add("From level " + fishing.techniques().creatures() + " a catch can bank a sea-creature encounter: Sneak and cast to call it and fight it.");
        }
        return out;
    }

    private static String number(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** A vanilla tool tier by the material that has it: 1 stone, 2 iron, 3 diamond, 4 netherite (0: any). */
    public static String tierName(int tier) {
        return switch (tier) {
            case 0 -> "any";
            case 1 -> "stone";
            case 2 -> "iron";
            case 3 -> "diamond";
            case 4 -> "netherite";
            default -> "tier " + tier;
        };
    }

    // ── loading ───────────────────────────────────────────────────

    /** The custom items Fishing added on 2026-10-27: its species, its treasure, its creatures' drops. */
    static final List<String> FISHING_ITEMS = List.of(
            "silver_dace", "river_perch", "brook_char", "goldfin_trout", "old_whiskers", "riverking_sturgeon",
            "pond_bream", "reed_pike", "moonlit_carp", "emerald_tench",
            "mud_loach", "bog_eel", "mire_gar", "witchlight_bowfin",
            "sea_bass", "striped_mackerel", "silver_tarpon", "bluefin_tuna", "abyssal_angler",
            "reef_wrasse", "coral_snapper", "sunburst_parrotfish", "sailfin_marlin",
            "ice_cod", "arctic_char", "frostfin_halibut", "glacier_wolffish",
            "blind_cavefish", "glowgill", "dripstone_eel", "crystal_eye",
            "sunken_coins", "sea_glass", "drowned_locket", "pearl_of_the_depths",
            "lurker_scale", "bog_resin", "tide_pearl", "rime_shard", "anglers_lantern", "reef_eye", "monarchs_seal", "leviathan_scale");

    /** The items Fishing equipment added (bait and the reel). */
    static final List<String> TACKLE_ITEMS = List.of("grub_bait", "chum", "glowworm_bait", "wayfarers_bait", "auto_hook_reel");

    /**
     * Entries updates added to the shipped resources.yml, by section. A server whose resources.yml has edits of its own
     * keeps its file (ShippedConfig), so each of these that the file doesn't mention at all is taken from the plugin's
     * own file ({@link #addUpdates}); "id: off" in the server's file leaves one out. When an update adds an entry to the
     * shipped file, list it here.
     */
    static final Map<String, List<String>> ADDED_BY_UPDATES = addedByUpdates();

    private static Map<String, List<String>> addedByUpdates() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        // 2026-10-26, the Woodcutting loop
        m.put("resources", List.of("oak", "birch", "spruce", "jungle", "acacia", "cherry", "dark_oak", "mangrove", "pale_oak",
                "crimson_stem", "warped_stem"));
        m.put("items", List.of("heartwood"));
        m.put("upgrades", List.of("heartwood_grip", "heartwood_limbs"));
        // 2026-10-27, Fishing (its own "fishing:" section comes whole when a file has none: addUpdates)
        List<String> items = new ArrayList<>(m.get("items"));
        items.addAll(FISHING_ITEMS);
        m.put("items", List.copyOf(items));
        List<String> ups = new ArrayList<>(m.get("upgrades"));
        ups.addAll(List.of("scaled_grip", "rime_hook", "heartwood_handle", "pearl_float", "sea_glass_float", "lantern_bob",
                "resin_line", "reef_eye_lens", "pearl_inlay", "monarchs_blessing", "leviathan_line"));
        m.put("upgrades", List.copyOf(ups));
        // 2026-10-27, Fishing equipment: bait and the auto-hook reel (docs/design/FISHING_EQUIPMENT.md)
        List<String> tackleItems = new ArrayList<>(m.get("items"));
        tackleItems.addAll(TACKLE_ITEMS);
        m.put("items", List.copyOf(tackleItems));
        return Collections.unmodifiableMap(m);
    }


    /**
     * Adds to {@code server} (in memory: the file on disk never changes) each entry of {@link #ADDED_BY_UPDATES} that the
     * plugin's own file {@code shipped} has and {@code server} doesn't mention, so an edited file from an older version
     * still gets what the update added. Returns a note per section that got something.
     */
    public static List<String> addUpdates(ConfigurationSection server, ConfigurationSection shipped) {
        List<String> notes = new ArrayList<>();
        if (server == null || shipped == null) return notes;
        Set<String> taken = new java.util.HashSet<>(); // blocks the server's own resources already are: theirs win
        ConfigurationSection own = server.getConfigurationSection("resources");
        if (own != null) for (String id : own.getKeys(false)) {
            for (String b : own.getStringList(id + ".blocks")) taken.add(b.trim().toUpperCase(Locale.ROOT));
        }
        for (Map.Entry<String, List<String>> e : ADDED_BY_UPDATES.entrySet()) {
            List<String> added = new ArrayList<>();
            for (String id : e.getValue()) {
                String path = e.getKey() + "." + id;
                if (server.contains(path) || !(shipped.get(path) instanceof ConfigurationSection s)) continue;
                List<String> clash = s.getStringList("blocks").stream().filter(b -> taken.contains(b.trim().toUpperCase(Locale.ROOT))).toList();
                if (!clash.isEmpty()) {
                    notes.add(path + ": not added, your file already has " + String.join(", ", clash));
                    continue;
                }
                server.createSection(path, values(s));
                added.add(id);
            }
            if (!added.isEmpty()) notes.add(e.getKey() + ": " + String.join(", ", added) + " not in this file (an update added "
                    + (added.size() == 1 ? "it" : "them") + "), so the plugin's own " + (added.size() == 1 ? "entry is" : "entries are")
                    + " used; write \"<id>: off\" to leave one out");
        }
        // Fishing (2026-10-27) comes as a whole section: a file without "fishing:" gets the plugin's own; "fishing: off" none.
        if (!server.contains("fishing") && shipped.get("fishing") instanceof ConfigurationSection f) {
            server.createSection("fishing", values(f));
            notes.add("fishing: not in this file (an update added it), so the plugin's own section is used; write \"fishing: off\" to turn fishing's rules off");
        }
        // Fishing equipment (2026-10-27, docs/design/FISHING_EQUIPMENT.md) likewise: "tackle: off" turns the setup and bait off.
        if (!server.contains("tackle") && shipped.get("tackle") instanceof ConfigurationSection t) {
            server.createSection("tackle", values(t));
            notes.add("tackle: not in this file (an update added it), so the plugin's own section is used; write \"tackle: off\" to turn fishing equipment off");
        }
        return notes;
    }

    /** A section's values as plain maps and lists, for copying it into another configuration. */
    private static Map<String, Object> values(ConfigurationSection s) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (String k : s.getKeys(false)) {
            Object v = s.get(k);
            out.put(k, v instanceof ConfigurationSection c ? values(c) : v);
        }
        return out;
    }

    /** "id: off" (YAML reads off as false): an entry the owner left out on purpose, skipped without a warning. */
    private static boolean off(ConfigurationSection parent, String id) {
        Object v = parent.get(id);
        return Boolean.FALSE.equals(v) || "off".equalsIgnoreCase(String.valueOf(v));
    }

    public static GatheringCatalog from(ConfigurationSection root) {
        return from(root, java.util.function.UnaryOperator.identity());
    }

    /**
     * @param skills a skill name as written → the skill's real name, or null when there is none (so a
     *               typo never creates a skill nobody can see)
     */
    public static GatheringCatalog from(ConfigurationSection root, java.util.function.UnaryOperator<String> skills) {
        List<String> problems = new ArrayList<>();
        Map<String, ItemDef> items = new LinkedHashMap<>();
        ConfigurationSection is = root == null ? null : root.getConfigurationSection("items");
        if (is != null) for (String id : is.getKeys(false)) {
            if (off(is, id)) continue;
            ConfigurationSection s = is.getConfigurationSection(id);
            Material m = s == null ? null : material(s.getString("material"));
            if (m == null) { problems.add("item " + id + ": unknown material " + (s == null ? null : s.getString("material"))); continue; }
            items.put(id, new ItemDef(id, m, s.getString("name", id), List.copyOf(s.getStringList("lore")), s.getBoolean("vanilla", false)));
        }

        Map<String, ToolDef> tools = new LinkedHashMap<>();
        ConfigurationSection ts = root == null ? null : root.getConfigurationSection("tools");
        if (ts != null) for (String id : ts.getKeys(false)) {
            ConfigurationSection s = ts.getConfigurationSection(id);
            Material m = s == null ? null : material(s.getString("material"));
            List<String> why = new ArrayList<>();
            if (m == null) why.add("unknown material " + (s == null ? null : s.getString("material")));
            Set<String> kinds = new LinkedHashSet<>();
            if (s != null) for (String k : s.getStringList("kinds")) kinds.add(k.toLowerCase(Locale.ROOT));
            if (kinds.isEmpty()) why.add("no kinds");
            int tier = s == null ? 0 : s.getInt("tier", 0);
            if (tier < 0 || tier > 100) why.add("tier " + tier + " outside 0-100");
            if (!why.isEmpty()) { problems.add("tool " + id + ": " + String.join("; ", why)); continue; }
            tools.put(id, new ToolDef(id, m, s.getString("name", id), Collections.unmodifiableSet(kinds), tier));
        }

        Set<String> knownKinds = new java.util.HashSet<>(VANILLA_KINDS);
        for (ToolDef t : tools.values()) knownKinds.addAll(t.kinds());

        Map<Material, ResourceDef> byBlock = new EnumMap<>(Material.class);
        Map<String, ResourceDef> resources = new LinkedHashMap<>();
        ConfigurationSection rs = root == null ? null : root.getConfigurationSection("resources");
        if (rs != null) for (String id : rs.getKeys(false)) {
            if (off(rs, id)) continue;
            ConfigurationSection s = rs.getConfigurationSection(id);
            if (s == null) { problems.add("resource " + id + ": not a section"); continue; }
            List<String> why = new ArrayList<>();
            String written = s.getString("skill");
            String skill = written == null || written.isBlank() ? null : skills.apply(written.trim());
            if (written == null || written.isBlank()) why.add("no skill");
            else if (skill == null) why.add("unknown skill " + written);
            Set<Material> blocks = java.util.EnumSet.noneOf(Material.class);
            for (String b : s.getStringList("blocks")) {
                Material m = material(b);
                if (m == null) why.add("unknown block " + b);
                else if (byBlock.containsKey(m)) why.add(b + " is already resource " + byBlock.get(m).id());
                else blocks.add(m);
            }
            if (blocks.isEmpty() && why.isEmpty()) why.add("no blocks");
            double xp = s.getDouble("xp", 0);
            if (!(xp >= 0 && xp <= 100_000)) why.add("xp " + xp + " outside 0-100000");
            int level = s.getInt("level", 1);
            if (level < 1 || level > 1000) why.add("level " + level + " outside 1-1000");
            String tool = s.getString("tool");
            if (tool != null && !tool.isBlank() && !knownKinds.contains(tool.trim().toLowerCase(Locale.ROOT))) {
                why.add("unknown tool kind " + tool + " (vanilla: pickaxe, axe, shovel, hoe; or a kind a custom tool lists)");
            }
            int tier = s.getInt("tier", 0);
            if (tier < 0 || tier > 100) why.add("tier " + tier + " outside 0-100");
            Object fo = s.get("fortune");
            boolean fortune = false;
            Attributes.Yield fortuneYield = null;
            if (fo instanceof Boolean b) {
                fortune = b;
                if (b) fortuneYield = defaultYield(skill);
            } else if (fo != null) {
                String f = String.valueOf(fo).trim().toLowerCase(Locale.ROOT);
                if (f.equals("true") || f.equals("yes")) { fortune = true; fortuneYield = defaultYield(skill); }
                else if (f.equals("general")) fortune = true; // only Fortune that counts everywhere (a luck skill's)
                else if (!f.equals("false") && !f.equals("no")) {
                    fortuneYield = Attributes.Yield.parse(f);
                    if (fortuneYield == null) why.add("fortune " + fo + " is not true, false, ore, wood or general");
                    else fortune = true;
                }
            }
            TreeDef tree = null;
            ConfigurationSection tr = s.isConfigurationSection("tree") ? s.getConfigurationSection("tree") : null;
            if (tr != null) {
                Set<Material> leaves = java.util.EnumSet.noneOf(Material.class);
                for (String l : tr.getStringList("leaves")) {
                    Material m = material(l);
                    if (m == null) why.add("unknown leaves " + l);
                    else leaves.add(m);
                }
                if (leaves.isEmpty() && why.isEmpty()) why.add("a tree needs leaves");
                double heart = tr.getDouble("heartwood", 0);
                if (!(heart >= 0 && heart <= 1)) why.add("heartwood " + heart + " outside 0-1");
                double disc = tr.getDouble("discovery-xp", 0);
                if (!(disc >= 0 && disc <= 100_000)) why.add("discovery-xp " + disc + " outside 0-100000");
                int trunk = tr.getInt("trunk", 2);
                if (trunk < 1 || trunk > 5) why.add("trunk " + trunk + " outside 1-5");
                tree = new TreeDef(Collections.unmodifiableSet(leaves), heart, disc, trunk);
            }
            List<Drop> drops = new ArrayList<>();
            for (Map<?, ?> d : s.getMapList("drops")) {
                Object item = d.get("item");
                int min = num(d.get("min"), num(d.get("amount"), 1)), max = num(d.get("max"), min);
                String it = item == null ? null : String.valueOf(item);
                if (it == null || (items.get(it) == null && material(it) == null)) why.add("unknown drop " + it);
                else if (min < 0 || max < min || max > 64) why.add("drop " + it + " amounts " + min + "-" + max + " outside 0-64");
                else drops.add(new Drop(it, min, max));
            }
            if (!why.isEmpty()) { problems.add("resource " + id + ": " + String.join("; ", why)); continue; }
            ResourceDef def = new ResourceDef(id, skill, Collections.unmodifiableSet(blocks), tool == null ? null : tool.toLowerCase(Locale.ROOT),
                    tier, level, xp, fortune, List.copyOf(drops), fortuneYield, tree);
            resources.put(id, def);
            for (Material m : blocks) byBlock.put(m, def);
        }

        ConfigurationSection sm = root == null ? null : root.getConfigurationSection("smelting");
        String smeltingSkill = skill(sm, "smelting", skills, problems);
        Map<Material, Double> smelting = new EnumMap<>(Material.class);
        ConfigurationSection sx = sm == null ? null : sm.getConfigurationSection("xp");
        if (sx != null) for (String k : sx.getKeys(false)) {
            Material m = material(k);
            double v = sx.getDouble(k, -1);
            if (m == null || !(v >= 0 && v <= 10_000)) problems.add("smelting " + k + ": unknown item or xp outside 0-10000");
            else smelting.put(m, v);
        }

        ConfigurationSection fg = root == null ? null : root.getConfigurationSection("forging");
        String forgingSkill = skill(fg, "forging", skills, problems);
        Map<String, Double> perUnit = new LinkedHashMap<>();
        ConfigurationSection pu = fg == null ? null : fg.getConfigurationSection("per-unit");
        if (pu != null) for (String k : pu.getKeys(false)) {
            double v = pu.getDouble(k, -1);
            if (!(v >= 0 && v <= 10_000)) problems.add("forging per-unit " + k + ": outside 0-10000");
            else perUnit.put(k.toLowerCase(Locale.ROOT), v);
        }
        Map<Material, Double> overrides = new EnumMap<>(Material.class);
        ConfigurationSection ov = fg == null ? null : fg.getConfigurationSection("items");
        if (ov != null) for (String k : ov.getKeys(false)) {
            Material m = material(k);
            double v = ov.getDouble(k, -1);
            if (m == null || !(v >= 0 && v <= 10_000)) problems.add("forging " + k + ": unknown item or xp outside 0-10000");
            else overrides.put(m, v);
        }
        double netherite = fg == null ? 0 : Math.max(0, Math.min(10_000, fg.getDouble("netherite-upgrade", 0)));
        Set<String> needsSmelted = new LinkedHashSet<>();
        if (fg != null && fg.isList("needs-smelted")) {
            for (String m : fg.getStringList("needs-smelted")) needsSmelted.add(m.trim().toLowerCase(Locale.ROOT));
        } else {
            needsSmelted.addAll(List.of("iron", "gold", "copper")); // the metals farms make; a file without the key keeps the protection
        }

        ConfigurationSection q = root == null ? null : root.getConfigurationSection("quality");
        QualityRules quality = new QualityRules(skill(q, "quality", skills, problems),
                weights(q, "odds.at-level-1", new double[] {100, 0, 0, 0}, problems),
                weights(q, "odds.at-level-100", new double[] {100, 0, 0, 0}, problems),
                weights(q, "weapon-power", new double[4], problems),
                weights(q, "armor-fortitude", new double[4], problems),
                weights(q, "tool-fortune", new double[4], problems));

        ConfigurationSection fo = root == null ? null : root.getConfigurationSection("fortune");
        double perPoint = fo == null ? 0 : Math.max(0, Math.min(1, fo.getDouble("chance-per-point", 0)));
        double max = fo == null ? 0 : Math.max(0, Math.min(1, fo.getDouble("max-chance", 0)));
        ConfigurationSection wk = root == null ? null : root.getConfigurationSection("weak-resources");
        int gap = wk == null ? 20 : Math.max(0, wk.getInt("level-gap", 20));
        double factor = wk == null ? 0.25 : Math.max(0, Math.min(1, wk.getDouble("factor", 0.25)));

        WoodRules wood = woodRules(root == null ? null : root.getConfigurationSection("woodcutting"), items, problems);
        double weakTool = root == null ? 0.25 : Math.max(0, Math.min(1, root.getDouble("woodcutting.weak-axe", 0.25)));

        Map<String, UpgradeDef> upgrades = new LinkedHashMap<>();
        ConfigurationSection us = root == null ? null : root.getConfigurationSection("upgrades");
        if (us != null) for (String id : us.getKeys(false)) {
            if (off(us, id)) continue;
            ConfigurationSection u = us.getConfigurationSection(id);
            if (u == null) { problems.add("upgrade " + id + ": not a section"); continue; }
            List<String> why = new ArrayList<>();
            String item = u.getString("item");
            if (item == null || items.get(item) == null) why.add("unknown item " + item + " (items:)");
            Set<String> on = new LinkedHashSet<>();
            // "for", never "on": YAML reads a bare on as true
            for (String k : u.isList("for") ? u.getStringList("for") : List.of(u.getString("for", ""))) {
                String kind = k.trim().toLowerCase(Locale.ROOT);
                if (kind.isEmpty()) continue;
                if (!ITEM_KINDS.contains(kind)) why.add("unknown item kind " + k + " (" + String.join(", ", ITEM_KINDS) + ")");
                else on.add(kind);
            }
            if (on.isEmpty() && why.isEmpty()) why.add("for: names no item kind");
            Attributes.Stat stat = Attributes.Stat.parse(u.getString("stat"));
            if (stat == null || !stat.isSecondary()) why.add("stat " + u.getString("stat") + " is not a speed stat");
            double amount = u.getDouble("amount", 0);
            if (!(amount > 0 && amount <= 1)) why.add("amount " + amount + " outside (0, 1]");
            if (!why.isEmpty()) { problems.add("upgrade " + id + ": " + String.join("; ", why)); continue; }
            upgrades.put(id, new UpgradeDef(id, item, Collections.unmodifiableSet(on), stat, amount, u.getString("name", id)));
        }

        FishingRules fishing = FishingRules.from(root == null ? null : root.getConfigurationSection("fishing"), items, skills, problems);
        TackleRules tackle = fishing.enabled() ? TackleRules.from(root == null ? null : root.getConfigurationSection("tackle"), items, upgrades, problems)
                : TackleRules.NONE;

        return new GatheringCatalog(Collections.unmodifiableMap(byBlock), Collections.unmodifiableMap(resources),
                Collections.unmodifiableMap(tools), Collections.unmodifiableMap(items), smeltingSkill,
                Collections.unmodifiableMap(smelting), forgingSkill, Collections.unmodifiableMap(perUnit),
                Collections.unmodifiableMap(overrides), netherite, Collections.unmodifiableSet(needsSmelted), quality, perPoint, max, gap,
                factor, wood, weakTool, Collections.unmodifiableMap(upgrades), fishing, tackle, problems);
    }

    /** Fortune a resource's skill counts by default ({@code fortune: true}): Mining's ore, Woodcutting's wood; else the general part. */
    static Attributes.Yield defaultYield(String skill) {
        return Attributes.Yield.defaultFor(skill);
    }

    /** The {@code woodcutting:} section; anything missing keeps the defaults (WoodRules.DEFAULTS). */
    static WoodRules woodRules(ConfigurationSection w, Map<String, ItemDef> items, List<String> problems) {
        WoodRules d = WoodRules.DEFAULTS;
        if (w == null) return d;
        java.util.TreeMap<Integer, Integer> at = new java.util.TreeMap<>(d.fellingAt());
        ConfigurationSection fa = w.getConfigurationSection("felling.at");
        if (fa != null) {
            at.clear();
            for (String k : fa.getKeys(false)) {
                int level, logs = fa.getInt(k, -1);
                try { level = Integer.parseInt(k.trim()); } catch (NumberFormatException e) { level = -1; }
                if (level < 1 || level > 1000 || logs < 1 || logs > 1024) problems.add("woodcutting.felling.at " + k + ": level 1-1000 and logs 1-1024");
                else at.put(level, logs);
            }
        }
        int giant = w.getInt("felling.giant-trunks-at", d.giantAt());
        long rest = Math.round(w.getDouble("felling.rest-per-log", d.restPerLogMs() / 1000.0) * 1000);
        if (rest < 0 || rest > 60_000) { problems.add("woodcutting.felling.rest-per-log: 0-60 seconds"); rest = d.restPerLogMs(); }
        int halved = w.getInt("felling.rest-halved-at", d.restHalvedAt());
        int canopy = w.getInt("canopy-at", d.canopyAt());
        TreeScan.Reach reach = new TreeScan.Reach(w.getInt("reach.sideways", d.reach().sideways()), w.getInt("reach.up", d.reach().up()),
                w.getInt("reach.down", d.reach().down()));
        int minLeaves = w.getInt("min-natural-leaves", d.minLeaves());
        String heartwood = w.getString("heartwood.item", d.heartwoodItem());
        if (heartwood != null && !heartwood.isBlank() && items.get(heartwood) == null) {
            problems.add("woodcutting.heartwood.item: unknown item " + heartwood + " (items:) - no heartwood drops");
            heartwood = null;
        }
        double factor = w.getDouble("heartwood.level-factor", d.levelFactor());
        return new WoodRules(at, giant, rest, halved, canopy, reach, minLeaves, heartwood, factor,
                share(w, "grown", d.grown(), problems), share(w, "bone-meal", d.boneMeal(), problems));
    }

    /** {@code woodcutting.<key>}: the share of a wild tree's XP and heartwood a grown tree pays (each 0-1), or {@code dflt}. */
    private static WoodRules.Share share(ConfigurationSection w, String key, WoodRules.Share dflt, List<String> problems) {
        double xp = w.getDouble(key + ".xp", dflt.xp()), heartwood = w.getDouble(key + ".heartwood", dflt.heartwood());
        if (!(xp >= 0 && xp <= 1) || !(heartwood >= 0 && heartwood <= 1)) {
            problems.add("woodcutting." + key + ": xp and heartwood are shares from 0 to 1 - using " + dflt.xp() + " and " + dflt.heartwood());
            return dflt;
        }
        return new WoodRules.Share(xp, heartwood);
    }

    /** A material by name, without the server's registry (unit tests read the shipped file too). */
    static Material material(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return Material.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** {@code s}'s {@code skill:} (Smithing when unset or unknown). */
    private static String skill(ConfigurationSection s, String where, java.util.function.UnaryOperator<String> skills, List<String> problems) {
        String written = s == null ? null : s.getString("skill");
        if (written == null || written.isBlank()) written = "Smithing";
        String real = skills.apply(written.trim());
        if (real != null) return real;
        problems.add(where + ": unknown skill " + written + " - using Smithing");
        String smithing = skills.apply("Smithing");
        return smithing != null ? smithing : "Smithing";
    }

    private static int num(Object v, int dflt) {
        if (v instanceof Number n) return n.intValue();
        if (v == null) return dflt;
        try { return Integer.parseInt(String.valueOf(v).trim()); } catch (NumberFormatException e) { return Integer.MIN_VALUE; }
    }

    /** {@code key}'s common/fine/superior/masterwork numbers (each 0-10000), or {@code dflt}. */
    private static double[] weights(ConfigurationSection q, String key, double[] dflt, List<String> problems) {
        ConfigurationSection s = q == null ? null : q.getConfigurationSection(key);
        if (s == null) return dflt.clone();
        double[] out = new double[Quality.values().length];
        for (Quality ql : Quality.values()) {
            double v = s.getDouble(ql.id(), 0);
            if (!(v >= 0 && v <= 10_000)) { problems.add("quality " + key + "." + ql.id() + ": " + v + " outside 0-10000"); v = 0; }
            out[ql.ordinal()] = v;
        }
        return out;
    }
}

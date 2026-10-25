package Gathering;

import Attributes.Attribute;
import Attributes.AttributeService;
import Skills.SkillManager;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.LongAdder;

/**
 * Gathering (docs/design/GATHERING.md): what breaking a block earns, by the rules in
 * {@link GatherRules} and the definitions in resources.yml, and the extraction call a future
 * machine uses. Holds no per-block state: placed blocks live in chunk data ({@link PlacedBlocks}),
 * XP rides the skill system's coalesced saves. Per player it keeps only the XP fractions not paid
 * yet, on that player's thread.
 */
public final class GatheringService {

    /** One block as the rules see it, for a player and the item in their hand. */
    public record Evaluation(Block block, GatheringCatalog.ResourceDef resource, GatherRules.Method method, boolean placed,
                             GatherRules.Tool tool, boolean toolFits, boolean silk, GatherRules.Decision decision,
                             double xp, double bonusChance) {}

    /** What a machine's extraction did: the drops, or why it couldn't. */
    public record Extraction(boolean done, List<ItemStack> drops, String why) {}

    private volatile GatheringCatalog catalog = GatheringCatalog.empty();
    private final SkillManager skills;
    private final AttributeService attributes;
    private PlacedBlocks placed;
    private final Map<UUID, GatherRules.Carry> carries = new ConcurrentHashMap<>();
    private final LongAdder xpBlocks = new LongAdder(), bonuses = new LongAdder(), refusals = new LongAdder();

    public GatheringService(SkillManager skills, AttributeService attributes) {
        this.skills = skills;
        this.attributes = attributes;
    }

    public void setPlacedBlocks(PlacedBlocks placed) { this.placed = placed; }

    public PlacedBlocks placedBlocks() { return placed; }

    public GatheringCatalog catalog() { return catalog; }

    /** Put new definitions in use (load, /cerb reload). */
    public void setCatalog(GatheringCatalog c) {
        catalog = c != null ? c : GatheringCatalog.empty();
        GatheringCatalog.setCurrent(catalog);
    }

    /** How a player's break counts: by hand in survival or adventure, or in creative (nothing). */
    public static GatherRules.Method methodOf(Player p) {
        return p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR
                ? GatherRules.Method.CREATIVE : GatherRules.Method.MANUAL;
    }

    /** What {@code p} breaking {@code b} with the item in their main hand earns; null when it is no resource. */
    public Evaluation evaluate(Player p, Block b) {
        GatheringCatalog c = catalog;
        GatheringCatalog.ResourceDef r = c.resource(b.getType());
        if (r == null) return null;
        ItemStack hand = p.getInventory().getItemInMainHand();
        GatherRules.Method method = methodOf(p);
        boolean wasPlaced = placed != null && placed.isPlaced(b);
        GatherRules.Tool tool = GearItems.tool(hand, c);
        boolean fits = GatherRules.toolFits(r.tool(), r.tier(), tool == null ? null : tool.kinds(), tool == null ? 0 : tool.tier());
        boolean silk = !hand.getType().isAir() && hand.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
        boolean vanillaDrops = r.replacesDrops() || b.isPreferredTool(hand);
        GatherRules.Decision d = GatherRules.decide(method, wasPlaced, r.replacesDrops(), fits, vanillaDrops, silk, r.fortune());
        double xp = d.xp() ? GatherRules.xp(r.xp(), r.level(), skills.getSkillLevel(p, r.skill()), c.weakGap(), c.weakFactor()) : 0;
        double chance = d.bonus() ? bonusChance(p.getUniqueId()) : 0;
        return new Evaluation(b, r, method, wasPlaced, tool, fits, silk, d, xp, chance);
    }

    /** The player's chance of one extra drop now: their Fortune through resources.yml's rule. */
    public double bonusChance(UUID player) {
        GatheringCatalog c = catalog;
        return GatherRules.bonusChance(attributes.get(player).get(Attribute.FORTUNE), c.fortunePerPoint(), c.fortuneMax());
    }

    /** Pay an evaluation's XP (fractions carry over). Player's thread. */
    public void payXp(Player p, Evaluation ev) {
        if (!ev.decision().xp()) return;
        xpBlocks.increment();
        award(p, ev.resource().skill(), ev.xp());
    }

    /** Award {@code xp} of {@code skill}, keeping fractions per player until they make a whole point. Player's thread. */
    public void award(Player p, String skill, double xp) {
        if (skill == null || !(xp > 0)) return;
        if (!skills.hasLoadedSkills(p.getUniqueId())) return; // not loaded yet (or leaving): nothing to add to
        long whole = carries.computeIfAbsent(p.getUniqueId(), id -> new GatherRules.Carry()).add(skill, xp);
        if (whole > 0) skills.addXpAndCheckLevelUp(skill, whole, p);
    }

    /** Roll the bonus for an evaluation: true when one extra item drops. */
    public boolean rollBonus(Evaluation ev) {
        return ev.bonusChance() > 0 && ThreadLocalRandom.current().nextDouble() < ev.bonusChance();
    }

    public void countBonus() { bonuses.increment(); }

    public void countRefusal() { refusals.increment(); }

    /** A custom resource's drops: each drop's amount rolled between its min and max, one more on the first with a bonus. */
    public List<ItemStack> customDrops(GatheringCatalog.ResourceDef r, boolean bonus) {
        GatheringCatalog c = catalog;
        List<ItemStack> out = new ArrayList<>();
        boolean extra = bonus;
        for (GatheringCatalog.Drop d : r.drops()) {
            int n = d.min() + (d.max() > d.min() ? ThreadLocalRandom.current().nextInt(d.max() - d.min() + 1) : 0);
            if (extra && n > 0) { n++; extra = false; }
            if (n <= 0) continue;
            GatheringCatalog.ItemDef def = c.item(d.item());
            if (def != null) out.add(GearItems.makeItem(def, n));
            else {
                Material m = GatheringCatalog.material(d.item());
                if (m != null && m.isItem()) out.add(new ItemStack(m, Math.min(n, m.getMaxStackSize())));
            }
        }
        return out;
    }

    /**
     * Extraction by a machine (a future drill or quarry), through the same definitions: the block's
     * drops with {@code machineTool}, no XP and no bonus (GatherRules.Method.AUTOMATED). A natural
     * custom resource the tool can't gather is left in place. Runs on the block's region thread.
     */
    public Extraction extract(Block b, ItemStack machineTool) {
        GatheringCatalog c = catalog;
        ItemStack tool = machineTool == null ? new ItemStack(Material.AIR) : machineTool;
        GatheringCatalog.ResourceDef r = c.resource(b.getType());
        if (b.getType().isAir()) return new Extraction(false, List.of(), "nothing there");
        boolean wasPlaced = r != null && placed != null && placed.isPlaced(b);
        GatherRules.Tool t = GearItems.tool(tool, c);
        boolean fits = r == null || GatherRules.toolFits(r.tool(), r.tier(), t == null ? null : t.kinds(), t == null ? 0 : t.tier());
        if (r != null && r.replacesDrops() && !wasPlaced && !fits) {
            return new Extraction(false, List.of(), "needs " + needs(r));
        }
        GatherRules.Decision d = r == null ? GatherRules.Decision.NOTHING
                : GatherRules.decide(GatherRules.Method.AUTOMATED, wasPlaced, r.replacesDrops(), fits, true, false, r.fortune());
        List<ItemStack> drops = d.customDrops() ? customDrops(r, false) : new ArrayList<>(b.getDrops(tool));
        if (r != null && placed != null) placed.unmark(b);
        b.setType(Material.AIR);
        return new Extraction(true, drops, null);
    }

    /** "a drill (tier 5)", "a pickaxe": what a resource needs, for messages. */
    public static String needs(GatheringCatalog.ResourceDef r) {
        if (r.tool() == null || r.tool().isEmpty()) return "nothing special";
        String a = "aeiou".indexOf(r.tool().charAt(0)) >= 0 ? "an " : "a ";
        return a + r.tool() + (r.tier() > 0 ? " (tier " + r.tier() + ")" : "");
    }

    /** Forget a player's XP fractions (they left). */
    public void forget(UUID player) {
        carries.remove(player);
    }

    // Diagnostics
    public long xpBlocks() { return xpBlocks.sum(); }
    public long bonuses() { return bonuses.sum(); }
    public long refusals() { return refusals.sum(); }
}

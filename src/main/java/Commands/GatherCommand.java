package Commands;

import Gathering.ForgeListener;
import Gathering.GatherRules;
import Gathering.GatheringCatalog;
import Gathering.GatheringService;
import Gathering.GearItems;
import Gathering.Quality;
import cerberus.world.cerb.CerberusPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;

/**
 * {@code /cerb gather ...}: admin tools for gathering and forging (docs/design/GATHERING.md). The
 * caller already holds {@code cerb.admin} (checked by /cerb on every use). Nothing here changes a
 * player's progress: it inspects a block, hands out custom tools and items, sets a held item's
 * quality for tests, and runs a machine's extraction on one block.
 */
public final class GatherCommand {

    static final List<String> SUBS = List.of("info", "tool", "item", "quality", "extract", "stats", "credit", "tree");

    private final CerberusPlugin plugin;

    public GatherCommand(CerberusPlugin plugin) {
        this.plugin = plugin;
    }

    public void run(Player p, String[] args) {
        GatheringService svc = plugin.getGatheringService();
        if (svc == null) { p.sendMessage(ChatColor.RED + "Gathering is not loaded (see the server log)."); return; }
        String sub = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        switch (sub) {
            case "info" -> info(p, svc);
            case "tool" -> tool(p, svc, args);
            case "item" -> item(p, svc, args);
            case "quality" -> quality(p, args);
            case "extract" -> extract(p, svc);
            case "stats" -> stats(p, svc);
            case "credit" -> credit(p, svc, args);
            case "tree" -> tree(p, svc);
            default -> p.sendMessage(ChatColor.YELLOW + "Use: /cerb gather <info|tool <id>|item <id> [n]|quality <q>|extract|stats|credit <metal> <n>|tree>");
        }
    }

    /** The block you look at, as the rules see it with the item in your hand. */
    private void info(Player p, GatheringService svc) {
        Block b = p.getTargetBlockExact(6);
        GatheringService.Evaluation ev = b == null ? null : svc.evaluate(p, b);
        ItemStack hand = p.getInventory().getItemInMainHand();
        Quality q = GearItems.quality(hand);
        if (b == null) {
            p.sendMessage(ChatColor.GOLD + "Block: " + ChatColor.GRAY + "none in sight (look at one within 6 blocks)");
        } else {
            p.sendMessage(ChatColor.GOLD + "Block: " + ChatColor.WHITE + b.getType().name().toLowerCase(Locale.ROOT)
                    + ChatColor.GRAY + " at " + b.getX() + " " + b.getY() + " " + b.getZ());
        }
        if (b == null) {
            // nothing to evaluate
        } else if (ev == null) {
            p.sendMessage(ChatColor.GRAY + "  not a resource (no XP, no bonus)");
        } else {
            GatheringCatalog.ResourceDef r = ev.resource();
            GatherRules.Decision d = ev.decision();
            p.sendMessage(ChatColor.GRAY + "  resource " + ChatColor.WHITE + r.id() + ChatColor.GRAY + " (" + r.skill() + ", level " + r.level()
                    + ", " + r.xp() + " XP" + (r.fortune() ? ", fortune" : "") + (r.replacesDrops() ? ", custom drops" : "") + ")");
            p.sendMessage(ChatColor.GRAY + "  origin: " + ChatColor.WHITE + originName(ev.origin()) + (ev.placed() ? " (pays nothing)" : "")
                    + ChatColor.GRAY + "  method: " + ChatColor.WHITE
                    + ev.method().name().toLowerCase(Locale.ROOT) + ChatColor.GRAY + "  tool: " + ChatColor.WHITE
                    + (ev.tool() == null ? "none" : ev.tool().id() + " " + ev.tool().kinds() + " tier " + ev.tool().tier())
                    + ChatColor.GRAY + " fits: " + ChatColor.WHITE + ev.toolFits() + (ev.silk() ? ChatColor.GRAY + " (silk touch)" : ""));
            p.sendMessage(ChatColor.GRAY + "  breaking it now: " + ChatColor.WHITE
                    + (d.refuse() ? "refused, needs " + GatheringService.needs(r)
                    : (d.xp() ? String.format(Locale.ROOT, "%.2f XP", ev.xp()) : "no XP")
                    + (d.bonus() ? String.format(Locale.ROOT, ", %.1f%% bonus chance", ev.bonusChance() * 100) : ", no bonus")
                    + (d.customDrops() ? ", custom drops" : "")));
        }
        var snap = plugin.getAttributeService().get(p.getUniqueId());
        Attributes.WeaponClass wc = Attributes.WeaponClass.ofMaterial(hand.getType().name());
        p.sendMessage(ChatColor.GRAY + "  attributes now: " + ChatColor.WHITE + String.format(Locale.ROOT,
                "Power %.2f (%s), Fortitude %.2f, Fortune %.2f (ore %.2f, wood %.2f), wear resistance %.2f%%", snap.power(wc),
                wc.singular.toLowerCase(Locale.ROOT), snap.get(Attributes.Attribute.FORTITUDE), snap.get(Attributes.Attribute.FORTUNE),
                snap.fortune(Attributes.Yield.ORE), snap.fortune(Attributes.Yield.WOOD), snap.secondary(Attributes.Stat.WEAR_RESISTANCE) * 100));
        StringBuilder smelted = new StringBuilder();
        for (String metal : svc.catalog().needsSmelted()) {
            smelted.append(smelted.isEmpty() ? "" : ", ").append(metal).append(' ').append(ForgeListener.credit(p, metal));
        }
        if (!smelted.isEmpty()) p.sendMessage(ChatColor.GRAY + "  smelted metal for forging: " + ChatColor.WHITE + smelted);
        int maxWear = hand.getType().getMaxDurability();
        p.sendMessage(ChatColor.GRAY + "  in hand: " + ChatColor.WHITE + hand.getType().name().toLowerCase(Locale.ROOT)
                + (q != null ? ChatColor.GRAY + ", quality " + q.color + q.displayName : "")
                + (maxWear > 0 && hand.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dm
                        ? ChatColor.GRAY + ", wear " + dm.getDamage() + "/" + maxWear : "")
                + (GearItems.toolId(hand) != null ? ChatColor.GRAY + ", custom tool " + GearItems.toolId(hand) : "")
                + (GearItems.upgrades(hand).isEmpty() ? "" : ChatColor.GRAY + ", upgrades " + GearItems.upgrades(hand))
                + ChatColor.GRAY + String.format(Locale.ROOT, "  your bonus chance: ore %.1f%%, wood %.1f%%",
                svc.bonusChance(p.getUniqueId(), Attributes.Yield.ORE) * 100, svc.bonusChance(p.getUniqueId(), Attributes.Yield.WOOD) * 100));
    }

    private void tool(Player p, GatheringService svc, String[] args) {
        GatheringCatalog.ToolDef d = args.length > 2 ? svc.catalog().tool(args[2]) : null;
        if (d == null) { p.sendMessage(ChatColor.RED + "Tools in resources.yml: " + svc.catalog().tools().keySet()); return; }
        give(p, GearItems.makeTool(d));
        p.sendMessage(ChatColor.GREEN + "Gave " + d.name() + " (" + d.id() + ")");
    }

    private void item(Player p, GatheringService svc, String[] args) {
        GatheringCatalog.ItemDef d = args.length > 2 ? svc.catalog().item(args[2]) : null;
        if (d == null) { p.sendMessage(ChatColor.RED + "Items in resources.yml: " + svc.catalog().items().keySet()); return; }
        int n = 1;
        if (args.length > 3) try { n = Integer.parseInt(args[3]); } catch (NumberFormatException e) { n = 1; }
        give(p, GearItems.makeItem(d, n));
        p.sendMessage(ChatColor.GREEN + "Gave " + n + " " + d.name() + " (" + d.id() + ")");
    }

    /** Set the held gear's quality (a test helper: the same data forging writes). */
    private void quality(Player p, String[] args) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (GatheringCatalog.gear(hand.getType().name()) == null) { p.sendMessage(ChatColor.RED + "Hold a tool, weapon or armor piece."); return; }
        String want = args.length > 2 ? args[2] : "";
        if (want.equalsIgnoreCase("none")) {
            GearItems.clearQuality(hand);
            p.sendMessage(ChatColor.GREEN + "Quality removed.");
        } else {
            Quality q = Quality.parse(want);
            if (q == null) { p.sendMessage(ChatColor.RED + "Use: /cerb gather quality <common|fine|superior|masterwork|none>"); return; }
            GearItems.setQuality(hand, q);
            p.sendMessage(ChatColor.GREEN + "Quality set to " + q.color + q.displayName);
        }
        p.getInventory().setItemInMainHand(hand); // the equipment change re-reads it
    }

    /** A machine's extraction of the block you look at, with the item in your hand as the machine's tool. */
    private void extract(Player p, GatheringService svc) {
        Block b = p.getTargetBlockExact(6);
        if (b == null || !Bukkit.isOwnedByCurrentRegion(b)) { p.sendMessage(ChatColor.RED + "Look at a block within 6 blocks."); return; }
        String type = b.getType().name().toLowerCase(Locale.ROOT);
        GatheringService.Extraction x = svc.extract(b, p.getInventory().getItemInMainHand());
        if (!x.done()) { p.sendMessage(ChatColor.YELLOW + "Extract " + type + ": not done, " + x.why()); return; }
        for (ItemStack it : x.drops()) b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), it);
        StringBuilder sb = new StringBuilder();
        for (ItemStack it : x.drops()) {
            String id = GearItems.itemId(it);
            sb.append(sb.isEmpty() ? "" : ", ").append(it.getAmount()).append(' ').append(id != null ? id : it.getType().name().toLowerCase(Locale.ROOT));
        }
        p.sendMessage(ChatColor.GREEN + "Extract " + type + ": done (automated: no XP, no bonus). Drops: " + (sb.isEmpty() ? "nothing" : sb));
    }

    private void stats(Player p, GatheringService svc) {
        ForgeListener f = plugin.getForgeListener();
        var placed = svc.placedBlocks();
        p.sendMessage(ChatColor.GOLD + "Gathering since start: " + ChatColor.WHITE + svc.xpBlocks() + " paying blocks, " + svc.bonuses()
                + " bonus drops, " + svc.refusals() + " refused breaks");
        if (f != null) p.sendMessage(ChatColor.GOLD + "Forging: " + ChatColor.WHITE + f.smelted() + " smelted, " + f.forged() + " forged, "
                + f.upgraded() + " netherite upgrades");
        if (placed != null) p.sendMessage(ChatColor.GOLD + "Placed blocks: " + ChatColor.WHITE + placed.cachedChunks() + " chunks cached, "
                + placed.chunkReads() + " chunk reads, " + placed.writes() + " writes");
        var xp = plugin.getXpDisplay();
        if (xp != null) p.sendMessage(ChatColor.GOLD + "XP display: " + ChatColor.WHITE + xp.updates() + " HUD updates");
        var wc = plugin.getWoodcutting();
        if (wc != null) p.sendMessage(ChatColor.GOLD + "Woodcutting: " + ChatColor.WHITE + wc.fellings() + " fellings (" + wc.felledLogs()
                + " logs), " + wc.refusals() + " refused, " + wc.scans() + " tree scans, " + wc.canopyLeaves() + " canopy leaves, "
                + wc.heartwoods() + " heartwood, " + wc.discoveries() + " discoveries");
        var attrs = plugin.getAttributeService();
        if (attrs != null) p.sendMessage(ChatColor.GOLD + "Attribute snapshots built: " + ChatColor.WHITE + attrs.rebuildCount());
    }

    /** The tree you look at, as Felling sees it (docs/design/WOODCUTTING.md §5, §6). */
    private void tree(Player p, GatheringService svc) {
        Block b = p.getTargetBlockExact(6);
        Gathering.Woodcutting wc = plugin.getWoodcutting();
        GatheringCatalog c = svc.catalog();
        GatheringCatalog.ResourceDef r = b == null ? null : c.resource(b.getType());
        if (b == null || r == null || !r.isTree() || wc == null || !Bukkit.isOwnedByCurrentRegion(b)) {
            p.sendMessage(ChatColor.RED + "Look at a log of a wood in resources.yml, within 6 blocks.");
            return;
        }
        int level = plugin.getSkillManager().getSkillLevel(p, r.skill());
        Gathering.WoodRules w = c.wood();
        Gathering.TreeScan.Tree t = wc.inspect(b, r, 1024);
        Gathering.Origin origin = svc.placedBlocks() == null ? Gathering.Origin.NATURAL : svc.placedBlocks().origin(b);
        p.sendMessage(ChatColor.GOLD + "Tree: " + ChatColor.WHITE + r.displayName() + ChatColor.GRAY + " (level " + r.level() + ", " + r.xp()
                + " XP a log, " + GatheringCatalog.tierName(r.tier()) + " axe)");
        boolean nothing = Gathering.GatheringService.paysNothing(r, origin);
        double share = nothing ? 0 : w.xpShare(origin);
        p.sendMessage(ChatColor.GRAY + "  this log: " + ChatColor.WHITE + originName(origin)
                + (share >= 1 ? "" : share <= 0 ? " (pays nothing)" : String.format(Locale.ROOT, " (pays %.0f%%)", share * 100))
                + ChatColor.GRAY + "  logs: " + ChatColor.WHITE + t.size() + (t.capped() ? "+" : "")
                + ChatColor.GRAY + "  its natural leaves: " + ChatColor.WHITE + t.naturalLeaves() + (t.naturalLeaves() >= Gathering.TreeScan.LEAF_COUNT_LIMIT ? "+" : "")
                + ChatColor.GRAY + "  trunk: " + ChatColor.WHITE + (t.giant() ? "giant (2x2)" : "single")
                + ChatColor.GRAY + "  base: " + ChatColor.WHITE + t.baseWidth() + " wide (a trunk: up to " + r.tree().trunk() + ")"
                + (t.build() ? ChatColor.YELLOW + "  touches a build" : "")
                + (t.edge() ? ChatColor.YELLOW + "  (reaches past what this region can read)" : ""));
        int cap = w.capacity(level);
        String fell = cap <= 0 ? "no: Felling opens at " + r.skill() + " " + w.firstFellingLevel()
                : t.size() > cap + 1 ? "no: more than " + cap + " logs besides the cut"
                : !t.natural(w.minLeaves()) ? "no: none of its own leaves (a build of logs)"
                : t.build() ? "no: it touches a build (planks, glass, a door...)"
                : t.baseWidth() > r.tree().trunk() ? "no: its base is wider than a trunk"
                : t.giant() && !w.giantTrunks(level) ? "no: giant trunks from " + w.giantAt()
                : "yes (" + w.fellingName(level) + ", up to " + cap + " logs)";
        long rest = wc.restLeft(p.getUniqueId());
        p.sendMessage(ChatColor.GRAY + "  Felling at your " + r.skill() + " " + level + ": " + ChatColor.WHITE + fell
                + (rest > 0 ? ChatColor.GRAY + ", ready in " + (rest + 999) / 1000 + " s" : ""));
        p.sendMessage(ChatColor.GRAY + "  heartwood chance per log: " + ChatColor.WHITE
                + String.format(Locale.ROOT, "%.2f%%", nothing ? 0 : w.heartwoodChance(r.tree().heartwood(), level, origin) * 100)
                + ChatColor.GRAY + "  discovered: " + ChatColor.WHITE + wc.discovered(p).contains(r.id()));
    }

    /** "natural", "placed", "grown", "bone meal": where a block came from, for these messages. */
    private static String originName(Gathering.Origin o) {
        return o == Gathering.Origin.BONEMEAL ? "bone meal" : o.name().toLowerCase(Locale.ROOT);
    }

    /** Test helper: set your smelted-metal units for forging (what smelting would have given). */
    private void credit(Player p, GatheringService svc, String[] args) {
        String metal = args.length > 2 ? args[2].toLowerCase(Locale.ROOT) : "";
        if (!svc.catalog().needsSmelted().contains(metal) || args.length < 4) {
            p.sendMessage(ChatColor.RED + "Use: /cerb gather credit <" + String.join("|", svc.catalog().needsSmelted()) + "> <units>");
            return;
        }
        int n;
        try { n = Math.max(0, Integer.parseInt(args[3])); } catch (NumberFormatException e) { p.sendMessage(ChatColor.RED + "Units must be a number."); return; }
        ForgeListener.addCredit(p, metal, (long) n - ForgeListener.credit(p, metal));
        p.sendMessage(ChatColor.GREEN + "Smelted " + metal + " for forging: " + ForgeListener.credit(p, metal) + " units");
    }

    private static void give(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }
}

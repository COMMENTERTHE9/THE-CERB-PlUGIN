package Gathering;

import Attributes.AttributeRules;
import Attributes.AttributeService;
import Attributes.AttributeSource;
import Attributes.Contribution;
import Skills.SkillManager;
import io.papermc.paper.potion.PotionMix;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BrewingStartEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Alchemy (docs/design/ALCHEMY.md): brewing at the stand, and the Cerberus brews it makes.
 * <ul>
 *   <li><b>XP only for completed brews</b>, to the player who started them: a click in a brewing stand's window arms that
 *       stand for a moment, and the stand's start event binds the brew to the player then (their level and whether they
 *       were in creative). A start nobody's click armed (a hopper) binds nobody. The brew event pays per bottle brewed.</li>
 *   <li><b>Cerberus brews</b> are Paper brewing mixes (an Awkward Potion and a reagent); the result is set at the brew event:
 *       the brew at the starter's tier, or a Mundane Potion when nobody (or somebody below its level) started it.</li>
 *   <li><b>Drinking:</b> an elixir is a timed effect through the attribute engine (one at a time, saved with its end time);
 *       a draught heals the Cerberus health pool for a few seconds (one at a time; another is refused while it heals).</li>
 * </ul>
 * Folia: a stand's events run on its region thread; a payment is handed to the player's own thread.
 */
public final class Alchemy implements Listener, AttributeSource {

    /** On a brewed bottle: "id:tier". */
    public static final NamespacedKey BREW = new NamespacedKey("cerberus", "brew");
    /** On the player: the running elixir, "id:tier:endMillis". */
    public static final NamespacedKey ELIXIR = new NamespacedKey("cerberus", "elixir");
    /** On the player: the elixir end the one-minute heads-up was said for. */
    public static final NamespacedKey ELIXIR_WARNED = new NamespacedKey("cerberus", "elixir_warned");
    /** On the player: the brews they have made, "id,id". */
    public static final NamespacedKey MADE = new NamespacedKey("cerberus", "brews_made");
    /** On a brewing stand while it brews: who started it, "uuid:level:creative" (a restart mid-brew keeps it). */
    public static final NamespacedKey STARTED_BY = new NamespacedKey("cerberus", "brew_started_by");
    public static final String SOURCE_PREFIX = "elixir:";
    /** How long a click in a stand's window arms it for the start it causes. */
    static final long ARM_MS = 1500;
    static final long WARN_BEFORE_MS = 60_000;
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    /** A running elixir. */
    public record Active(String id, Quality tier, long end) {
        String encode() { return id + ":" + tier.id() + ":" + end; }

        static Active decode(String s) {
            if (s == null) return null;
            String[] p = s.split(":");
            if (p.length != 3) return null;
            Quality q = Quality.parse(p[1]);
            try { return q == null ? null : new Active(p[0], q, Long.parseLong(p[2])); } catch (NumberFormatException e) { return null; }
        }
    }

    /** Who started a stand's brew: their level then, creative, and when (a stand broken mid-brew leaves its binding behind). */
    private record Binding(UUID player, int level, boolean creative, long at) {
        String encode() { return player + ":" + level + ":" + creative; }

        static Binding decode(String s) {
            if (s == null) return null;
            String[] p = s.split(":");
            if (p.length != 3) return null;
            try { return new Binding(UUID.fromString(p[0]), Integer.parseInt(p[1]), Boolean.parseBoolean(p[2]), System.currentTimeMillis()); }
            catch (IllegalArgumentException e) { return null; }
        }
    }

    private record Stand(UUID world, int x, int y, int z) {
        static Stand of(Block b) { return new Stand(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ()); }

        static Stand of(Location l) { return new Stand(l.getWorld().getUID(), l.getBlockX(), l.getBlockY(), l.getBlockZ()); }
    }

    private record Arm(UUID player, long until) {}

    /** A draught healing: its brew, its seconds, how many are done. */
    private static final class Draught {
        final String brew;
        final Quality tier;
        final int seconds;
        int done;
        boolean paused;

        Draught(String brew, Quality tier, int seconds) { this.brew = brew; this.tier = tier; this.seconds = seconds; }
    }

    /** How a first brew of a kind is shown (chat and a sound, set by the plugin). */
    @FunctionalInterface
    public interface Announcer {
        void firstBrew(Player p, AlchemyRules.Brew b, int made, int all);
    }

    private final Plugin plugin;
    private final GatheringService gathering;
    private final SkillManager skills;
    private final AttributeService attributes;
    private final BiConsumer<Player, String> notice;
    private final Consumer<Player> refresh;
    private volatile BiConsumer<Player, Double> heal = (p, a) -> {};
    private volatile java.util.function.ToDoubleFunction<Player> maxHealth = p -> 100;
    private volatile java.util.function.Predicate<UUID> pvpTagged = id -> false;
    private volatile Cooking.HeadsUp headsUp = (p, text, force) -> true;
    private volatile Announcer announcer;
    private final Map<Stand, Arm> armed = new ConcurrentHashMap<>();
    private final Map<Stand, Binding> bound = new ConcurrentHashMap<>();
    private final Map<UUID, Active> active = new ConcurrentHashMap<>();
    private final Map<UUID, Draught> draughts = new ConcurrentHashMap<>();
    private final Map<UUID, Set<String>> made = new ConcurrentHashMap<>();
    private final Set<NamespacedKey> mixes = ConcurrentHashMap.newKeySet();
    private volatile String registered = "";
    private final LongAdder vanillaBottles = new LongAdder(), brewBottles = new LongAdder(), mundane = new LongAdder(), unbound = new LongAdder(),
            elixirs = new LongAdder(), draughtsDrunk = new LongAdder(), refused = new LongAdder(), healTicks = new LongAdder(), warnings = new LongAdder(),
            mixRegistrations = new LongAdder(), resumed = new LongAdder();

    public Alchemy(Plugin plugin, GatheringService gathering, SkillManager skills, AttributeService attributes, BiConsumer<Player, String> notice,
                   Consumer<Player> refresh) {
        this.plugin = plugin;
        this.gathering = gathering;
        this.skills = skills;
        this.attributes = attributes;
        this.notice = notice;
        this.refresh = refresh;
    }

    public void setHealth(BiConsumer<Player, Double> heal, java.util.function.ToDoubleFunction<Player> maxHealth) {
        this.heal = heal;
        this.maxHealth = maxHealth;
    }

    public void setPvpTagged(java.util.function.Predicate<UUID> tagged) { this.pvpTagged = tagged; }

    public void setHeadsUp(Cooking.HeadsUp headsUp) { this.headsUp = headsUp; }

    public void setAnnouncer(Announcer a) { this.announcer = a; }

    private AlchemyRules rules() { return gathering.catalog().alchemy(); }

    // ── the brews as brewing mixes ───────────────────────────────

    /**
     * (Re)register {@code c}'s brews as brewing mixes (the global region thread). Only a reload that adds or removes a brew
     * touches the server's list of mixes (which brewing stands read on their own threads): each mix asks the catalog of the
     * moment whether its brew still exists and what it takes, and the bottle it makes is rebuilt when the brew completes.
     */
    public void registerMixes(GatheringCatalog c) {
        AlchemyRules r = c.alchemy();
        StringBuilder sig = new StringBuilder();
        if (r.enabled()) for (AlchemyRules.Brew b : r.brews()) sig.append(b.id()).append(';');
        if (sig.toString().equals(registered)) return;
        mixRegistrations.increment();
        for (NamespacedKey k : mixes) Bukkit.getPotionBrewer().removePotionMix(k);
        mixes.clear();
        if (r.enabled()) for (AlchemyRules.Brew b : r.brews()) {
            NamespacedKey key = new NamespacedKey(plugin, "brew_" + b.id());
            RecipeChoice input = PotionMix.createPredicateChoice(Alchemy::plainAwkward);
            String id = b.id();
            RecipeChoice ingredient = PotionMix.createPredicateChoice(it -> {
                GatheringCatalog now = gathering.catalog();
                AlchemyRules.Brew brew = now.alchemy().enabled() ? now.alchemy().brew(id) : null;
                return brew != null && reagentFor(now, brew, it) != null;
            });
            try {
                Bukkit.getPotionBrewer().addPotionMix(new PotionMix(key, makeBrew(b, Quality.COMMON, r), input, ingredient));
                mixes.add(key);
            } catch (RuntimeException e) {
                plugin.getLogger().warning("[Alchemy] brew " + b.id() + ": the server refused its brewing mix - left out (" + e.getMessage() + ")");
            }
        }
        registered = sig.toString();
    }

    /** An Awkward Potion that is no Cerberus brew: the base every Cerberus brew starts from. */
    static boolean plainAwkward(ItemStack it) {
        if (it == null || it.getType() != Material.POTION || !(it.getItemMeta() instanceof PotionMeta m)) return false;
        return m.getBasePotionType() == PotionType.AWKWARD && !m.getPersistentDataContainer().has(BREW, PersistentDataType.STRING);
    }

    /**
     * What {@code it} is as brew {@code b}'s reagent: "plain" (the herb itself, any quality), "lift" (the brew's lifting
     * rare reagent), "rare" (the brew's own rare reagent), or null (not this brew's).
     */
    static String reagentFor(GatheringCatalog c, AlchemyRules.Brew b, ItemStack it) {
        if (it == null || it.getType().isAir()) return null;
        String id = GearItems.itemId(it);
        if (id == null) return b.takes(it.getType()) ? "plain" : null;
        HerbalismRules h = c.herbalism();
        for (HerbalismRules.Rare rare : h.rares()) {
            if (!rare.item().equals(id)) continue;
            if (rare.id().equals(b.rare())) return "rare";
            if (rare.id().equals(b.lift())) return "lift";
        }
        return null;
    }

    /** True for a Cerberus item one of Alchemy's brews takes (the brewing guard lets these into a stand). */
    public boolean isReagent(ItemStack it) {
        GatheringCatalog c = gathering.catalog();
        if (!c.alchemy().enabled()) return false;
        for (AlchemyRules.Brew b : c.alchemy().brews()) if (reagentFor(c, b, it) != null) return true;
        return false;
    }

    /** A bottle of {@code b} at tier {@code q}: its name, colour, tier and effect in its lore, its id on the item, no vanilla effect. */
    public static ItemStack makeBrew(AlchemyRules.Brew b, Quality q, AlchemyRules r) {
        ItemStack it = new ItemStack(Material.POTION);
        PotionMeta m = (PotionMeta) it.getItemMeta();
        m.setBasePotionType(PotionType.MUNDANE); // no vanilla effect; a mundane potion has almost no vanilla brewing use
        m.setColor(Color.fromRGB(b.color()));
        m.displayName(text("§r" + q.color + b.name()).decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(text("§7" + b.kind().label() + " · " + q.color + q.displayName).decoration(TextDecoration.ITALIC, false));
        for (String line : effectLines(b, q, r)) lore.add(text("§9" + line).decoration(TextDecoration.ITALIC, false));
        lore.add(text("§8Alchemy " + b.level()).decoration(TextDecoration.ITALIC, false));
        m.lore(lore);
        m.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        m.getPersistentDataContainer().set(BREW, PersistentDataType.STRING, b.id() + ":" + q.id());
        it.setItemMeta(m);
        return it;
    }

    /** What a bottle does, a line each: "Vitality +9.6", "Heals 30% of max health over 5 s", "10 min". */
    public static List<String> effectLines(AlchemyRules.Brew b, Quality q, AlchemyRules r) {
        List<String> out = new ArrayList<>();
        if (b.kind() == AlchemyRules.Kind.DRAUGHT) {
            out.add("Heals " + CookingRules.trim(r.restoreShare(b, q) * 100) + "% of max health over " + b.restore().seconds() + " s");
        } else {
            for (CookingRules.Effect e : b.effects()) out.add(e.describe(r.potency(q)));
            out.add(Cooking.duration(r.durationMs(b, q)));
        }
        return out;
    }

    private static Component text(String legacy) { return LEGACY.deserialize(legacy); }

    /** A bottle's brew and tier, or null. */
    public static String[] brewOf(ItemStack it) {
        if (it == null || it.getType() != Material.POTION || !it.hasItemMeta()) return null;
        String s = it.getItemMeta().getPersistentDataContainer().get(BREW, PersistentDataType.STRING);
        if (s == null) return null;
        String[] p = s.split(":");
        return p.length == 2 ? p : null;
    }

    // ── who started a brew ───────────────────────────────────────

    /**
     * A click that puts something into a brewing stand (a bottle, the reagent, its fuel) arms the stand for the start it
     * causes. A click that puts nothing in (an empty slot, taking out, outside the window) arms nothing: a stand hoppers
     * feed brews for nobody, however often someone clicks in its window. The stand's region thread.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory() instanceof BrewerInventory inv) || !(e.getWhoClicked() instanceof Player p)) return;
        boolean inStand = e.getRawSlot() >= 0 && e.getRawSlot() < inv.getSize();
        ItemStack brought = switch (e.getAction()) {
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> e.getHotbarButton() >= 0 ? p.getInventory().getItem(e.getHotbarButton())
                    : p.getInventory().getItemInOffHand();
            case MOVE_TO_OTHER_INVENTORY -> e.getCurrentItem();
            default -> null;
        };
        if (putsInto(e.getAction(), inStand, brought != null && !brought.getType().isAir(), standTakes(brought))) arm(inv, p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory() instanceof BrewerInventory inv && e.getWhoClicked() instanceof Player p
                && e.getRawSlots().stream().anyMatch(slot -> slot < inv.getSize())) arm(inv, p);
    }

    /**
     * True when a click puts an item into a brewing stand: placed or swapped into one of its slots ({@code inStand},
     * {@code brings}: a number-key swap brings an item, not only takes one), or shift-clicked over from the player's own
     * inventory ({@code takes}: an item the stand accepts). Anything else puts nothing in.
     */
    static boolean putsInto(InventoryAction a, boolean inStand, boolean brings, boolean takes) {
        return switch (a) {
            case PLACE_ALL, PLACE_SOME, PLACE_ONE, SWAP_WITH_CURSOR, PLACE_FROM_BUNDLE -> inStand;
            case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> inStand && brings;
            case MOVE_TO_OTHER_INVENTORY -> !inStand && takes;
            default -> false;
        };
    }

    /** True for an item a shift-click moves into a brewing stand: a bottle, its fuel, a vanilla ingredient or a brew's reagent. */
    private boolean standTakes(ItemStack it) {
        if (it == null || it.getType().isAir()) return false;
        Material m = it.getType();
        if (m == Material.POTION || m == Material.SPLASH_POTION || m == Material.LINGERING_POTION || m == Material.BLAZE_POWDER) return true;
        if (AlchemyRules.VANILLA_INGREDIENTS.contains(m)) return true;
        return GearItems.itemId(it) == null ? rules().reagents().contains(m) : isReagent(it);
    }

    private void arm(BrewerInventory inv, Player p) {
        Location l = inv.getLocation();
        if (l == null || l.getWorld() == null) return;
        if (armed.size() > 4096) { long now = System.currentTimeMillis(); armed.values().removeIf(a -> a.until() < now); }
        armed.put(Stand.of(l), new Arm(p.getUniqueId(), System.currentTimeMillis() + ARM_MS));
    }

    /**
     * The stand starts brewing: bound to the player whose click caused it, or to nobody (a hopper). The binding is kept on
     * the stand too, so a brew a restart interrupts still knows who started it when it completes.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onStart(BrewingStartEvent e) {
        Stand s = Stand.of(e.getBlock());
        Arm a = armed.remove(s);
        Player p = a == null || a.until() < System.currentTimeMillis() ? null : Bukkit.getPlayer(a.player());
        if (p == null) { bound.remove(s); keep(e.getBlock(), null); return; }
        AlchemyRules r = rules();
        int level = r.enabled() ? skills.getSkillLevel(p, r.skill()) : 0;
        long now = System.currentTimeMillis();
        if (bound.size() > 1024) bound.values().removeIf(b -> now - b.at() > 120_000); // a brew takes 20 s
        Binding b = new Binding(p.getUniqueId(), level, GatheringService.methodOf(p) == GatherRules.Method.CREATIVE, now);
        bound.put(s, b);
        keep(e.getBlock(), b);
    }

    /** Write (or with null, clear) the binding on the stand itself: its block data, saved with the chunk. The stand's thread. */
    private static void keep(Block stand, Binding b) {
        if (!(stand.getState(false) instanceof org.bukkit.block.BrewingStand live)) return;
        if (b == null) live.getPersistentDataContainer().remove(STARTED_BY);
        else live.getPersistentDataContainer().set(STARTED_BY, PersistentDataType.STRING, b.encode());
    }

    /** The binding kept on the stand (a brew started before a restart), removed as it is read. The stand's thread. */
    private Binding kept(Block stand) {
        if (!(stand.getState(false) instanceof org.bukkit.block.BrewingStand live)) return null;
        String s = live.getPersistentDataContainer().get(STARTED_BY, PersistentDataType.STRING);
        if (s == null) return null;
        live.getPersistentDataContainer().remove(STARTED_BY);
        Binding b = Binding.decode(s);
        if (b != null) resumed.increment();
        return b;
    }

    // ── the brew completes ───────────────────────────────────────

    /**
     * The brew is done: Cerberus brews become the brew at the starter's tier (or a Mundane Potion), a Cerberus brew already
     * in the stand is left as it is, and the starter is paid per bottle. The stand's region thread; the payment on the
     * player's own.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrew(BrewEvent e) {
        Stand s = Stand.of(e.getBlock());
        Binding who = bound.remove(s);
        if (who == null) who = kept(e.getBlock()); // started before a restart
        else keep(e.getBlock(), null);
        AlchemyRules r = rules();
        BrewerInventory inv = e.getContents();
        List<ItemStack> results = e.getResults();
        GatheringCatalog c = gathering.catalog();
        ItemStack ingredient = inv.getIngredient();
        int vanilla = 0, ours = 0;
        double xp = 0;
        AlchemyRules.Brew brewed = null, refusedBrew = null;
        Quality tierMade = null;
        for (int i = 0; i < results.size() && i < 3; i++) {
            ItemStack in = inv.getItem(i), out = results.get(i);
            if (brewOf(in) != null) { results.set(i, in.clone()); continue; } // a Cerberus brew is never brewed again
            String[] made = brewOf(out);
            if (made != null) {
                AlchemyRules.Brew b = r.enabled() ? r.brew(made[0]) : null;
                if (b == null || who == null || who.level() < b.level()) {
                    results.set(i, mundane());
                    mundane.increment();
                    if (b != null) refusedBrew = b;
                    continue;
                }
                String kind = reagentFor(c, b, ingredient);
                // The reagent's own quality lifts the brew, a rare reagent's too (Herbalism rolls theirs as it does a herb's).
                Quality q = r.tier(who.level(), kind == null ? null : GearItems.cropQuality(ingredient), "lift".equals(kind));
                results.set(i, makeBrew(b, q, r));
                ours++;
                xp += b.xp();
                brewed = b;
                tierMade = q;
            } else if (out != null && !out.getType().isAir() && (in == null || !out.isSimilar(in))) {
                vanilla++;
                if (r.enabled()) xp += r.vanillaXp();
            }
        }
        vanillaBottles.add(vanilla);
        brewBottles.add(ours);
        if (who == null) { if (vanilla + ours > 0) unbound.increment(); return; } // a hopper's brew: vanilla's output, nobody paid
        Binding starter = who;
        Player p = Bukkit.getPlayer(starter.player());
        if (p == null || !r.enabled()) return; // the starter has left: the brew still finished
        double pay = xp;
        AlchemyRules.Brew b = brewed, below = refusedBrew;
        Quality q = tierMade;
        int bottles = ours;
        p.getScheduler().run(plugin, t -> {
            if (below != null) notice.accept(p, "§7" + below.name() + " needs Alchemy " + below.level() + ": the stand brewed a Mundane Potion.");
            if (starter.creative()) { if (pay > 0) gathering.withheld(p, r.skill(), Manager.XpIntegrity.whyNot(p)); return; }
            if (pay > 0) gathering.award(p, r.skill(), pay);
            if (b != null) {
                notice.accept(p, "§d✦ " + q.color + q.displayName + " §d" + b.name() + " §7× " + bottles);
                discover(p, b);
            }
        }, null);
    }

    private static ItemStack mundane() {
        ItemStack it = new ItemStack(Material.POTION);
        PotionMeta m = (PotionMeta) it.getItemMeta();
        m.setBasePotionType(PotionType.MUNDANE);
        it.setItemMeta(m);
        return it;
    }

    /** The brews {@code p} has made (their ids). Player's thread. */
    public Set<String> made(Player p) {
        return made.computeIfAbsent(p.getUniqueId(), id -> {
            String saved = p.getPersistentDataContainer().get(MADE, PersistentDataType.STRING);
            Set<String> out = ConcurrentHashMap.newKeySet();
            if (saved != null) for (String s : saved.split(",")) if (!s.isBlank()) out.add(s.trim());
            return out;
        });
    }

    /** Forget the brews {@code p} has made (admin, for tests). Player's thread. */
    public void resetMade(Player p) {
        made.remove(p.getUniqueId());
        p.getPersistentDataContainer().remove(MADE);
    }

    private void discover(Player p, AlchemyRules.Brew b) {
        Set<String> have = made(p);
        if (!have.add(b.id())) return;
        p.getPersistentDataContainer().set(MADE, PersistentDataType.STRING, String.join(",", new LinkedHashSet<>(have)));
        Announcer a = announcer;
        if (a != null) a.firstBrew(p, b, have.size(), rules().brews().size());
    }

    // ── drinking ─────────────────────────────────────────────────

    /** One draught at a time: a second is refused before the drink happens, and the bottle is kept. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrinkRefuse(PlayerItemConsumeEvent e) {
        String[] tag = brewOf(e.getItem());
        if (tag == null) return;
        AlchemyRules r = rules();
        AlchemyRules.Brew b = r.enabled() ? r.brew(tag[0]) : null;
        if (b == null || b.kind() != AlchemyRules.Kind.DRAUGHT) return;
        Draught d = draughts.get(e.getPlayer().getUniqueId());
        if (d != null && d.done < d.seconds) {
            e.setCancelled(true);
            refused.increment();
            notice.accept(e.getPlayer(), "§7A draught is still working (" + (d.seconds - d.done) + " s): drink this one after.");
        }
    }

    /** The drink happened (nothing cancelled it, another plugin's rule included): the elixir or the draught takes effect. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrink(PlayerItemConsumeEvent e) {
        String[] tag = brewOf(e.getItem());
        if (tag == null) return;
        Player p = e.getPlayer();
        AlchemyRules r = rules();
        AlchemyRules.Brew b = r.enabled() ? r.brew(tag[0]) : null;
        Quality q = Quality.parse(tag[1]);
        if (b == null || q == null) { p.sendMessage("§7This brew is no longer made on this server: a plain potion."); return; }
        UUID id = p.getUniqueId();
        if (b.kind() == AlchemyRules.Kind.DRAUGHT) {
            draughtsDrunk.increment();
            notice.accept(p, q.color + q.displayName + " §d" + b.name() + " §7· " + effectLines(b, q, r).get(0));
            p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.6f, 1.2f);
            restore(p, b, q);
            return;
        }
        Active before = active.get(id);
        long now = System.currentTimeMillis();
        Active next = new Active(b.id(), q, now + r.durationMs(b, q));
        active.put(id, next);
        p.getPersistentDataContainer().set(ELIXIR, PersistentDataType.STRING, next.encode());
        p.getPersistentDataContainer().remove(ELIXIR_WARNED);
        elixirs.increment();
        attributes.markDirty(id);
        refresh.accept(p);
        String change;
        if (before != null && before.end() > now && !before.id().equals(b.id())) {
            AlchemyRules.Brew old = r.brew(before.id());
            change = "replaces " + (old == null ? "your last elixir" : old.name());
        } else if (before != null && before.end() > now) {
            change = "time refreshed: " + Cooking.duration(next.end() - now);
        } else {
            change = Cooking.duration(next.end() - now);
        }
        notice.accept(p, q.color + q.displayName + " §d" + b.name() + " §7· " + change);
        List<String> lines = effectLines(b, q, r);
        p.sendMessage("§d✦ " + b.name() + " §7(" + q.color + q.displayName + "§7): " + String.join(", ", lines.subList(0, lines.size() - 1))
                + " §8· " + Cooking.duration(next.end() - now) + (change.startsWith("replaces") ? " §7(" + change + ")" : ""));
        p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 0.6f, 1.0f);
        scheduleEnd(p, next);
        scheduleWarning(p, next);
    }

    /** A draught's healing, a heal a second for its seconds: paused while PvP-tagged, ended by death or leaving. */
    private void restore(Player p, AlchemyRules.Brew b, Quality q) {
        UUID id = p.getUniqueId();
        double share = rules().restoreShare(b, q);
        int seconds = b.restore().seconds();
        Draught token = new Draught(b.id(), q, seconds);
        draughts.put(id, token);
        int[] waited = {0};
        p.getScheduler().runAtFixedRate(plugin, task -> {
            if (draughts.get(id) != token || !p.isOnline() || p.isDead() || token.done >= seconds || waited[0] > seconds * 3) {
                draughts.remove(id, token);
                task.cancel();
                return;
            }
            waited[0]++;
            if (pvpTagged.test(id)) { token.paused = true; return; } // paused, not lost: the rest comes after the fight
            token.paused = false;
            heal.accept(p, maxHealth.applyAsDouble(p) * share / seconds);
            token.done++;
            healTicks.increment();
        }, null, 1L, 20L);
    }

    private void scheduleEnd(Player p, Active a) {
        long ticks = Math.max(1, (a.end() - System.currentTimeMillis() + 49) / 50);
        p.getScheduler().runDelayed(plugin, t -> {
            if (active.get(p.getUniqueId()) != a) return; // replaced, refreshed or ended meanwhile
            end(p, true);
        }, null, ticks);
    }

    /** The one-minute heads-up, as a meal's: one delayed task; a replaced, refreshed or ended elixir never warns. */
    private void scheduleWarning(Player p, Active a) {
        if (Long.valueOf(a.end()).equals(p.getPersistentDataContainer().get(ELIXIR_WARNED, PersistentDataType.LONG))) return;
        long left = a.end() - System.currentTimeMillis();
        if (left <= WARN_BEFORE_MS + 5_000) return;
        p.getScheduler().runDelayed(plugin, t -> warn(p, a, 10), null, Math.max(1, (left - WARN_BEFORE_MS) / 50));
    }

    private void warn(Player p, Active a, int tries) {
        if (active.get(p.getUniqueId()) != a || !p.isOnline()) return;
        long left = a.end() - System.currentTimeMillis();
        if (left <= 0) return;
        AlchemyRules.Brew b = rules().brew(a.id());
        String text = "§d" + (b == null ? "Your elixir" : b.name()) + " §7wears off in " + Cooking.duration(left);
        if (headsUp.show(p, text, tries <= 1)) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.45f, 1.6f);
            p.getPersistentDataContainer().set(ELIXIR_WARNED, PersistentDataType.LONG, a.end());
            warnings.increment();
            return;
        }
        p.getScheduler().runDelayed(plugin, t -> warn(p, a, tries - 1), null, 20L);
    }

    /** End {@code p}'s elixir and draught now (admin). Player's thread. */
    public void clear(Player p) {
        draughts.remove(p.getUniqueId());
        end(p, false);
    }

    private void end(Player p, boolean wornOff) {
        UUID id = p.getUniqueId();
        Active a = active.remove(id);
        p.getPersistentDataContainer().remove(ELIXIR);
        p.getPersistentDataContainer().remove(ELIXIR_WARNED);
        if (a == null) return;
        attributes.markDirty(id);
        refresh.accept(p);
        if (wornOff) {
            AlchemyRules.Brew b = rules().brew(a.id());
            notice.accept(p, "§7" + (b == null ? "Your elixir" : b.name()) + " wore off.");
        }
    }

    /** {@code p}'s running elixir, or null. */
    public Active active(UUID player) {
        Active a = active.get(player);
        return a == null || a.end() <= System.currentTimeMillis() ? null : a;
    }

    /** Seconds {@code p}'s draught still heals (0: none). */
    public int draughtLeft(UUID player) {
        Draught d = draughts.get(player);
        return d == null ? 0 : Math.max(0, d.seconds - d.done);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        draughts.remove(p.getUniqueId());
        if (active.containsKey(p.getUniqueId())) end(p, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Active a = Active.decode(p.getPersistentDataContainer().get(ELIXIR, PersistentDataType.STRING));
        if (a != null && (a.end() <= System.currentTimeMillis() || rules().brew(a.id()) == null)) {
            p.getPersistentDataContainer().remove(ELIXIR);
            a = null;
        }
        if (a == null) return;
        active.put(p.getUniqueId(), a);
        attributes.markDirty(p.getUniqueId());
        Active run = a;
        p.getScheduler().runDelayed(plugin, t -> { refresh.accept(p); scheduleEnd(p, run); scheduleWarning(p, run); }, null, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        active.remove(id); // saved on the player with its end time
        draughts.remove(id); // a draught lasts seconds: not saved
        made.remove(id);
    }

    // ── shown (docs/design/ACTIVE_EFFECTS.md) ────────────────────

    /** {@code p}'s running elixir and draught as the menus show them. Player's thread. */
    public void effects(Player p, long now, List<Effects.ActiveEffect> out) {
        AlchemyRules r = rules();
        Active a = active.get(p.getUniqueId());
        if (a != null && a.end() > now) {
            AlchemyRules.Brew b = r.brew(a.id());
            if (b != null) out.add(new Effects.ActiveEffect("elixir:" + a.id() + ":" + a.tier().id(), Effects.ActiveEffect.Category.ELIXIR, b.name(),
                    "Elixir · " + a.tier().displayName + " · Alchemy", Material.POTION, a.end(), null, contributions(b, a.tier(), r), List.of(), null));
        }
        Draught d = draughts.get(p.getUniqueId());
        if (d != null && d.done < d.seconds) {
            AlchemyRules.Brew b = r.brew(d.brew);
            if (b != null) {
                String what = CookingRules.trim(r.restoreShare(b, d.tier) * 100) + "% of max health over " + d.seconds + " s";
                out.add(new Effects.ActiveEffect("draught:" + d.brew, Effects.ActiveEffect.Category.ELIXIR, b.name(),
                        "Draught · " + d.tier.displayName + " · Alchemy", Material.POTION, now + (d.seconds - d.done) * 1000L, null, List.of(), List.of(),
                        new Effects.ActiveEffect.Phase("Healing", what + ", a heal a second", now + (d.seconds - d.done) * 1000L, d.paused)));
            }
        }
    }

    // ── the elixir in the attribute engine ───────────────────────

    @Override public String id() { return "elixir"; }

    @Override
    public List<Contribution> contributions(UUID player, AttributeRules ruleSet) {
        Active a = active(player);
        if (a == null) return List.of();
        AlchemyRules r = rules();
        AlchemyRules.Brew b = r.brew(a.id());
        return b == null ? List.of() : contributions(b, a.tier(), r);
    }

    /** Pure: what an elixir at tier {@code q} adds, every number times the tier's potency. */
    public static List<Contribution> contributions(AlchemyRules.Brew b, Quality q, AlchemyRules r) {
        double pot = r.potency(q);
        String label = "Elixir · " + b.name() + " (" + q.displayName + ")";
        List<Contribution> out = new ArrayList<>();
        for (CookingRules.Effect e : b.effects()) {
            double v = e.amount() * pot;
            if (e.stat() != null) out.add(Contribution.stat(SOURCE_PREFIX + b.id(), label, e.stat(), v, null));
            else if (e.yield() != null) out.add(Contribution.fortune(SOURCE_PREFIX + b.id(), label, v, e.yield()));
            else out.add(Contribution.attribute(SOURCE_PREFIX + b.id(), label, e.attribute(), v, null));
        }
        return out;
    }

    /** Diagnostics for /cerb alchemy stats. */
    public String stats() {
        return "vanilla bottles " + vanillaBottles.sum() + ", Cerberus brews " + brewBottles.sum() + ", Mundane instead " + mundane.sum()
                + ", brews nobody started " + unbound.sum() + ", elixirs drunk " + elixirs.sum() + ", draughts " + draughtsDrunk.sum()
                + " (refused " + refused.sum() + "), healing ticks " + healTicks.sum() + ", heads-ups " + warnings.sum()
                + ", running now " + active.size() + ", stands bound " + bound.size() + ", resumed after a restart " + resumed.sum()
                + ", brewing mixes registered " + mixRegistrations.sum() + "x";
    }
}

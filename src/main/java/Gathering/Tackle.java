package Gathering;

import Attributes.AttributeRules;
import Attributes.AttributeService;
import Attributes.AttributeSource;
import Attributes.Contribution;
import Attributes.Stat;
import Skills.SkillManager;
import cerberus.world.cerb.CerberusPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

/**
 * Fishing equipment at run time (docs/design/FISHING_EQUIPMENT.md §5-§9): each player's setup, presets and Bait Bag
 * ({@link FishingGear}, saved on the player), the setup's stats in the attribute engine (this is an
 * {@link AttributeSource}: any rod a player fishes with uses the active preset, and an old upgraded rod's upgrades count
 * once with it), the bait a cast holds and when it is used up or put back, fitting parts, converting old rods, and the
 * crafting recipes for bait and the reel. Rules are {@link TackleRules} (resources.yml {@code tackle:}).
 *
 * <p>Threads (Folia): every change to a player's equipment runs on their own region thread; the attribute engine reads an
 * immutable view of their active parts from any thread.
 */
public final class Tackle implements AttributeSource, Listener {

    /** The player's equipment (FishingGear.encode), saved with the player. */
    public static final NamespacedKey GEAR = new NamespacedKey("cerberus", "fishing_gear");
    public static final String SOURCE_PREFIX = "tackle:";

    private final CerberusPlugin plugin;
    private final Supplier<GatheringCatalog> catalog;
    private final SkillManager skills;
    private final AttributeService attributes;
    private final EquipmentSource equipment;

    private final Map<UUID, FishingGear> gear = new ConcurrentHashMap<>();
    /** What the attribute engine reads from any thread: the active preset's parts and the owned parts (immutable copies). */
    private final Map<UUID, View> views = new ConcurrentHashMap<>();
    /** Players whose saved equipment this version can't read: kept as it is, never written over. */
    private final Set<UUID> unreadable = ConcurrentHashMap.newKeySet();
    /** "Bait depleted" was said and the bag hasn't had usable bait since. */
    private final Set<UUID> depletedTold = ConcurrentHashMap.newKeySet();
    /** A preset rename waiting for the player's next chat line: the preset and until when. */
    private final Map<UUID, long[]> renaming = new ConcurrentHashMap<>();
    private final Set<NamespacedKey> recipeKeys = ConcurrentHashMap.newKeySet();
    private volatile String registeredRecipes = "";
    /** The materials a locked recipe's result is made of: a crafting grid showing anything else needs no recipe lookup. */
    private volatile Set<Material> lockedResults = Set.of();

    private final LongAdder holds = new LongAdder(), used = new LongAdder(), putBack = new LongAdder(), fits = new LongAdder(),
            conversions = new LongAdder(), restocked = new LongAdder();

    private record View(Set<String> installed, Set<String> owned, String preset) {
        static final View EMPTY = new View(Set.of(), Set.of(), "");
    }

    public Tackle(CerberusPlugin plugin, Supplier<GatheringCatalog> catalog, SkillManager skills, AttributeService attributes,
                  EquipmentSource equipment) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.skills = skills;
        this.attributes = attributes;
        this.equipment = equipment;
    }

    private TackleRules rules() { return catalog.get().tackle(); }

    private String skill() {
        String s = catalog.get().fishing().skill();
        return s == null ? "Fishing" : s;
    }

    public int level(Player p) { return skills.getSkillLevel(p.getUniqueId(), skill()); }

    // ── each player's equipment ──────────────────────────────────

    /** {@code p}'s equipment, read from the player once and kept while they are online. Player's thread. */
    public FishingGear gear(Player p) {
        return gear.computeIfAbsent(p.getUniqueId(), id -> {
            String raw = p.getPersistentDataContainer().get(GEAR, PersistentDataType.STRING);
            FishingGear g;
            try {
                g = FishingGear.decode(raw);
            } catch (IllegalArgumentException e) {
                unreadable.add(id);
                plugin.getLogger().warning("[Fishing] " + p.getName() + "'s fishing equipment was saved by a newer version; it is kept "
                        + "as it is and nothing is changed until that version runs again.");
                g = new FishingGear();
            }
            views.put(id, view(g));
            return g;
        });
    }

    /** False when this player's saved equipment can't be read here: nothing may change it. */
    public boolean writable(Player p) {
        gear(p);
        return !unreadable.contains(p.getUniqueId());
    }

    private static View view(FishingGear g) {
        return new View(Set.copyOf(g.activePreset().parts()), Set.copyOf(g.owned()), g.activePreset().name());
    }

    /** Write {@code p}'s equipment back onto the player; {@code setup} when the parts that count changed (stats rebuild). */
    public void save(Player p, boolean setup) {
        UUID id = p.getUniqueId();
        FishingGear g = gear.get(id);
        if (g == null || unreadable.contains(id)) return;
        p.getPersistentDataContainer().set(GEAR, PersistentDataType.STRING, g.encode());
        views.put(id, view(g));
        if (setup) {
            attributes.markDirty(id);
            Manager.SecondaryEffects sec = plugin.getSecondaryEffects();
            if (sec != null) sec.apply(p);
        }
    }

    // ── the setup in the attribute engine ────────────────────────

    @Override public String id() { return "tackle"; }

    @Override
    public List<Contribution> contributions(UUID player, AttributeRules ruleSet) {
        return at(player, skills.getSkillLevel(player, skill()));
    }

    /** With Fishing at {@code level}: a level-up's "before" leaves out the parts that open at the new level. */
    @Override
    public List<Contribution> contributionsAt(UUID player, AttributeRules ruleSet, String skill, int level) {
        return skill().equalsIgnoreCase(skill) ? at(player, level) : contributions(player, ruleSet);
    }

    private List<Contribution> at(UUID player, int level) {
        GatheringCatalog c = catalog.get();
        View v = views.getOrDefault(player, View.EMPTY);
        Set<String> active = c.tackle().active(v.installed(), v.owned(), level);
        return contributions(active, rodUpgrades(player), c, v.preset());
    }

    /** The upgrades on the rod in {@code player}'s main hand (an old upgraded rod), or none. */
    private List<String> rodUpgrades(UUID player) {
        EquipmentSource.Piece hand = equipment == null ? null : equipment.worn(player).hand();
        return hand != null && "rod".equals(hand.kind()) ? hand.upgrades() : List.of();
    }

    /**
     * Pure: what a setup adds. The active parts' upgrades and the rod's own, each once (an old rod's Scaled grip and the
     * setup's count once), every one through the engine's caps and diminishing returns like any other source.
     */
    public static List<Contribution> contributions(Set<String> activeParts, List<String> rodUpgrades, GatheringCatalog c, String preset) {
        TackleRules t = c.tackle();
        Set<String> fromSetup = TackleRules.countedUpgrades(activeParts, t.parts(), List.of());
        Set<String> counted = TackleRules.countedUpgrades(activeParts, t.parts(), rodUpgrades);
        List<Contribution> out = new ArrayList<>(counted.size());
        for (String id : counted) {
            GatheringCatalog.UpgradeDef u = c.upgrade(id);
            if (u == null || u.stat() == null || !(u.amount() > 0) || !u.on().contains("rod")) continue;
            boolean setup = fromSetup.contains(id);
            String label = (setup ? "Fishing setup" + (preset == null || preset.isEmpty() ? "" : " (" + preset + ")") : "Rod in hand") + " · " + u.name();
            out.add(Contribution.stat(SOURCE_PREFIX + (setup ? "setup" : "rod"), label, u.stat(), u.amount(), null));
        }
        return out;
    }

    /** One fishing stat before and after a change: effective values (after caps), the raw change, and whether it is on. */
    public record StatChange(Stat stat, double now, double then, double raw, boolean enabled) {
        public boolean changes() { return Math.abs(then - now) > 1e-9 || Math.abs(raw) > 1e-9; }
        /** A raw change the cap leaves (almost) nothing of: under 0.1 percentage points. */
        public boolean capped() { return Math.abs(raw) > 1e-9 && Math.abs(then - now) < 0.001; }
    }

    public static final List<Stat> FISHING_STATS = List.of(Stat.LURE_SPEED, Stat.TREASURE_CHANCE, Stat.SEA_CREATURE_CHANCE);

    /**
     * What installing exactly {@code proposed} in the active preset would change, after the engine's caps and diminishing
     * returns (the same calculator, fed the proposed setup instead of the current one). Player's thread.
     */
    public List<StatChange> preview(Player p, Set<String> proposed) {
        UUID id = p.getUniqueId();
        GatheringCatalog c = catalog.get();
        TackleRules t = c.tackle();
        FishingGear g = gear(p);
        int level = level(p);
        List<String> rod = rodUpgrades(id);
        Set<String> nowActive = t.active(g.activePreset().parts(), g.owned(), level);
        Set<String> thenActive = t.active(proposed, g.owned(), level);
        List<Contribution> nowC = contributions(nowActive, rod, c, g.activePreset().name());
        List<Contribution> thenC = contributions(thenActive, rod, c, g.activePreset().name());
        Attributes.AttributeSnapshot now = attributes.preview(id, cs -> replace(cs, nowC));
        Attributes.AttributeSnapshot then = attributes.preview(id, cs -> replace(cs, thenC));
        List<StatChange> out = new ArrayList<>();
        for (Stat s : FISHING_STATS) {
            double raw = sum(thenC, s) - sum(nowC, s);
            out.add(new StatChange(s, now.secondary(s), then.secondary(s), raw, attributes.secondaryEffect(s) == AttributeService.Effect.ACTIVE));
        }
        return out;
    }

    private static List<Contribution> replace(List<Contribution> all, List<Contribution> tackle) {
        List<Contribution> out = new ArrayList<>(all.size() + tackle.size());
        for (Contribution x : all) if (!x.sourceId().startsWith(SOURCE_PREFIX)) out.add(x);
        out.addAll(tackle);
        return out;
    }

    private static double sum(List<Contribution> cs, Stat s) {
        double t = 0;
        for (Contribution x : cs) if (x.stat() == s) t += x.amount();
        return t;
    }

    /** The parts that count for {@code p} now. Player's thread. */
    public Set<String> active(Player p) {
        FishingGear g = gear(p);
        return rules().active(g.activePreset().parts(), g.owned(), level(p));
    }

    // ── a cast's equipment (docs/design/FISHING_EQUIPMENT.md §5: a snapshot per cast) ──

    /**
     * What one cast fishes with, taken when it is cast: the specialist, the bait held on its line (null: none), whether the
     * auto-hook reel reels it, the species the player had caught (for the discovery factors), and the stats a bait changes
     * (null: the player's own, unchanged).
     */
    public record CastGear(TackleRules.Part specialist, TackleRules.Bait bait, boolean autoReel, Set<String> caught,
                           Double lureSpeed, Double creatureChance) {
        public static final CastGear NONE = new CastGear(null, null, false, Set.of(), null, null);

        /** The discovery factor for species not caught yet (Surveyor's Float and a discovery bait, before the cap). */
        public double discovery() {
            double f = 1.0;
            if (specialist != null && specialist.special() == TackleRules.Special.DISCOVERY) f *= specialist.value();
            if (bait != null && bait.purpose() == TackleRules.Purpose.DISCOVERY) f *= bait.factor();
            return f;
        }

        public double night() { return bait != null && bait.purpose() == TackleRules.Purpose.NIGHT ? bait.factor() : 1.0; }

        /** The weight roll's exponent (the Specimen Hook flattens it; else vanilla's 2.5). */
        public double kgExponent() {
            return specialist != null && specialist.special() == TackleRules.Special.SPECIMEN ? TackleRules.specimenExponent(specialist.value()) : 2.5;
        }

        public boolean weighs() { return discovery() > 1 || night() > 1; }
    }

    /**
     * A cast (player's thread): its snapshot, and one bait held from the first enabled slot whose bait can apply now (time
     * and level; the water is checked at the catch). A Sneak-cast call uses no bait and is never reeled automatically.
     */
    public CastGear onCast(Player p, UUID hook, boolean call, int level, FishingJournal journal) {
        TackleRules t = rules();
        if (!t.enabled()) return CastGear.NONE;
        FishingGear g = gear(p);
        Set<String> active = t.active(g.activePreset().parts(), g.owned(), level);
        TackleRules.Part specialist = t.specialist(active);
        boolean reel = !call && g.autoReel() && t.hasAutoReel(active);
        TackleRules.Bait bait = null;
        if (!call && writable(p) && g.held() == null) {
            int slots = t.bagSlots(level);
            for (int i = 0; i < slots; i++) {
                FishingGear.Slot s = g.slot(i);
                if (s.empty() || !g.activePreset().usesSlot(i)) continue;
                TackleRules.Bait b = t.baitById(s.bait());
                if (b == null || !canApplyAtCast(b, p.getWorld(), level)) continue; // stays stored, shown inactive
                if (b.stat() != null && paused(p)) continue; // its stat is paused in PvP: it would be spent for nothing
                if (b.purpose() == TackleRules.Purpose.DISCOVERY && journal.readOnly()) continue; // no record of what was caught
                if (g.hold(i, hook)) {
                    bait = b;
                    holds.increment();
                    save(p, false);
                }
                break;
            }
        }
        boolean discovery = (specialist != null && specialist.special() == TackleRules.Special.DISCOVERY)
                || (bait != null && bait.purpose() == TackleRules.Purpose.DISCOVERY);
        Set<String> caught = discovery ? Set.copyOf(journal.species().keySet()) : Set.of();
        Double lure = null, creature = null;
        if (bait != null && bait.stat() != null) {
            double v = withBait(p, bait);
            if (bait.stat() == Stat.LURE_SPEED) lure = v;
            else creature = v;
        }
        return new CastGear(specialist, bait, reel, caught, lure, creature);
    }

    /** True while {@code p}'s secondary stats are paused (PvP combat). */
    private boolean paused(Player p) {
        Manager.SecondaryEffects sec = plugin.getSecondaryEffects();
        return sec != null && sec.paused(p.getUniqueId());
    }

    /** The bait's stat as the engine gives it with the bait added to everything else (0 while paused in PvP). */
    private double withBait(Player p, TackleRules.Bait bait) {
        if (paused(p)) return 0;
        Contribution c = Contribution.stat(SOURCE_PREFIX + "bait", "Bait · " + bait.name(), bait.stat(), bait.amount(), null);
        return attributes.preview(p.getUniqueId(), cs -> {
            List<Contribution> out = new ArrayList<>(cs);
            out.add(c);
            return out;
        }).secondary(bait.stat());
    }

    /** Whether a bait can apply when cast: night bait only at night, encounter bait only from the level creatures start. */
    boolean canApplyAtCast(TackleRules.Bait b, World w, int level) {
        return switch (b.purpose()) {
            case SPEED, DISCOVERY -> !b.night() || night(w);
            case ENCOUNTERS -> level >= catalog.get().fishing().techniques().creatures() && (!b.night() || night(w));
            case NIGHT -> night(w);
        };
    }

    static boolean night(World w) {
        long t = w.getTime() % 24000;
        return t >= 13000 && t < 23000;
    }

    /**
     * Whether the held bait applied to a catch at {@code m} (the bite's moment): speed always; encounters where a creature
     * of the water lives at this level; night bait where a night-only species can bite; discovery bait where an
     * undiscovered species can bite. Pure.
     */
    public static boolean appliedTo(TackleRules.Bait b, FishingRules r, FishingRules.Moment m, int level, Set<String> caught) {
        if (b == null) return false;
        return switch (b.purpose()) {
            case SPEED -> true;
            case ENCOUNTERS -> m != null && m.water() != null && level >= r.techniques().creatures() && !r.creaturesOf(m.water(), level).isEmpty();
            case NIGHT -> m != null && m.night() && r.eligible(m, level).stream().anyMatch(s -> s.when().time() == FishingRules.Time.NIGHT);
            case DISCOVERY -> m != null && r.eligible(m, level).stream().anyMatch(s -> !caught.contains(s.id()));
        };
    }

    /**
     * A catch that really happened with cast {@code hook} (player's thread): its held bait is used up when it applied,
     * else put back. Then restocking and the one "depleted" line.
     */
    public void onCatch(Player p, UUID hook, CastGear cast, FishingRules.Moment m, int level) {
        FishingGear g = gear.get(p.getUniqueId());
        if (g == null || g.held() == null || !g.held().hook().equals(hook)) return;
        TackleRules t = rules();
        TackleRules.Bait b = t.baitById(g.held().bait());
        int slot = g.held().slot();
        if (b != null && cast != null && appliedTo(b, catalog.get().fishing(), m, level, cast.caught())) {
            g.consume(hook);
            used.increment();
            if (g.slot(slot).empty() && g.restock()) restock(p, g, slot, b);
            // Said once when this catch used the last bait the active preset can take (not when a switch skips a full slot).
            if (g.slot(slot).empty() && noBaitLeft(g, level) && depletedTold.add(p.getUniqueId())) {
                p.sendMessage(ChatColor.GRAY + "Bait depleted. Normal fishing continues.");
            }
        } else {
            giveBack(p, g, g.release(hook, t.bagSlots(level)));
            putBack.increment();
        }
        save(p, false);
    }

    /** The cast {@code hook} ended without a catch (reeled in, lost, hooked a mob, cancelled, logout): its bait goes back. */
    public void castEnded(Player p, UUID hook) {
        FishingGear g = gear.get(p.getUniqueId());
        if (g == null || g.held() == null || (hook != null && !g.held().hook().equals(hook))) return;
        giveBack(p, g, g.release(hook, rules().bagSlots(level(p))));
        putBack.increment();
        save(p, false);
    }

    /** A held bait with no room left in the bag comes back as an item (dropped for its owner when the inventory is full). */
    private void giveBack(Player p, FishingGear g, String bait) {
        if (bait == null || bait.isEmpty()) return;
        TackleRules.Bait b = rules().baitById(bait);
        GatheringCatalog.ItemDef def = b == null ? null : catalog.get().item(b.item());
        if (def == null) return; // unknown here: can't be made into an item; it was one bait of a removed kind
        ItemStack it = GearItems.makeItem(def, 1);
        for (ItemStack left : p.getInventory().addItem(it).values()) {
            Item drop = p.getWorld().dropItem(p.getLocation(), left);
            drop.setOwner(p.getUniqueId());
        }
    }

    private boolean noBaitLeft(FishingGear g, int level) {
        TackleRules t = rules();
        for (int i = 0; i < t.bagSlots(level); i++) {
            if (!g.activePreset().usesSlot(i) || g.slot(i).empty()) continue;
            if (t.baitById(g.slot(i).bait()) != null) return false;
        }
        return true;
    }

    /** Opt-in restocking: the same bait, from the player's own inventory only, up to a full slot. */
    private void restock(Player p, FishingGear g, int slot, TackleRules.Bait b) {
        int want = rules().bagStack();
        int took = takeFromInventory(p.getInventory(), b.item(), want);
        if (took <= 0) return;
        int in = g.load(slot, b.id(), took, want);
        if (in < took) {
            GatheringCatalog.ItemDef def = catalog.get().item(b.item());
            if (def != null) p.getInventory().addItem(GearItems.makeItem(def, took - in)); // never more than was taken
        }
        restocked.increment();
        depletedTold.remove(p.getUniqueId());
        Manager.PlayerHUDManager hud = plugin.getPlayerHUDManager();
        if (hud != null) hud.flashIfIdle(p, "§7Bait restocked from your inventory: " + b.name() + " ×" + in, 2000);
    }

    /** Remove up to {@code n} items carrying custom item {@code itemId} from {@code inv}; returns how many were taken. */
    static int takeFromInventory(PlayerInventory inv, String itemId, int n) {
        int left = n;
        ItemStack[] contents = inv.getStorageContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (it == null || !itemId.equals(GearItems.itemId(it))) continue;
            int take = Math.min(left, it.getAmount());
            left -= take;
            if (take >= it.getAmount()) inv.setItem(i, null);
            else { it.setAmount(it.getAmount() - take); inv.setItem(i, it); }
        }
        return n - left;
    }

    /** How many items carrying custom item {@code itemId} {@code inv} holds. */
    static int countInInventory(PlayerInventory inv, String itemId) {
        int n = 0;
        for (ItemStack it : inv.getStorageContents()) if (it != null && itemId.equals(GearItems.itemId(it))) n += it.getAmount();
        return n;
    }

    // ── menus' actions (player's thread; each re-checks everything) ──

    /** The outcome of an action, with a line for the player. */
    public record Result(boolean ok, String message) {
        static Result no(String why) { return new Result(false, why); }
        static Result yes(String what) { return new Result(true, what); }
    }

    /** Fit a part: one of its item comes out of the inventory, the part is owned for good and goes into its presets. */
    public Result fit(Player p, String partId) {
        if (!writable(p)) return Result.no("Your fishing equipment was saved by a newer version of the plugin.");
        TackleRules t = rules();
        TackleRules.Part part = t.part(partId);
        if (part == null) return Result.no("That part isn't on this server.");
        FishingGear g = gear(p);
        if (g.owns(partId)) return Result.no("You already own the " + part.name() + ".");
        if (part.item() == null) return Result.no("The " + part.name() + " comes from a collection milestone.");
        GatheringCatalog.ItemDef def = catalog.get().item(part.item());
        if (def == null) return Result.no("Its item isn't on this server.");
        if (takeFromInventory(p.getInventory(), part.item(), 1) != 1) return Result.no("You need 1 " + def.name() + " in your inventory.");
        g.own(partId);
        installDefaults(g, t, partId);
        fits.increment();
        save(p, true);
        return Result.yes("Fitted the " + part.name() + " (1 " + def.name() + " used). It stays on your character for any rod.");
    }

    /** Give a part (a collection reward, an admin). True when it was new. */
    public boolean grantPart(Player p, String partId) {
        TackleRules t = rules();
        if (!writable(p) || t.part(partId) == null) return false;
        FishingGear g = gear(p);
        if (!g.own(partId)) return false;
        installDefaults(g, t, partId);
        save(p, true);
        return true;
    }

    /**
     * A newly owned part goes into its default presets; a specialist leaves out a preset that already has one (only one
     * counts there, and the player chose it).
     */
    private void installDefaults(FishingGear g, TackleRules t, String partId) {
        TackleRules.Part part = t.part(partId);
        for (int i : t.defaultPresets(partId, catalog.get().upgrades())) {
            if (part != null && part.specialist() && g.preset(i).parts().stream().map(t::part)
                    .anyMatch(o -> o != null && o.specialist())) continue;
            g.install(i, partId);
        }
    }

    /** Put a part into, or take it out of, preset {@code i}. Installing a specialist removes the other one there. */
    public Result toggle(Player p, int i, String partId) {
        if (!writable(p)) return Result.no("Your fishing equipment was saved by a newer version of the plugin.");
        FishingGear g = gear(p);
        if (!g.owns(partId)) return Result.no("You don't own that part.");
        TackleRules.Part part = rules().part(partId);
        if (g.preset(i).parts().contains(partId)) {
            g.uninstall(i, partId);
            save(p, true);
            return Result.yes("Took the " + (part == null ? partId : part.name()) + " out of " + g.preset(i).name() + ".");
        }
        if (part == null) return Result.no("That part isn't on this server: it stays owned but can't be used.");
        if (part.specialist()) for (String other : List.copyOf(g.preset(i).parts())) {
            TackleRules.Part o = rules().part(other);
            if (o != null && o.specialist()) g.uninstall(i, other);
        }
        g.install(i, partId);
        save(p, true);
        return Result.yes("Put the " + part.name() + " into " + g.preset(i).name() + ".");
    }

    public Result activate(Player p, int i) {
        if (!writable(p)) return Result.no("Your fishing equipment was saved by a newer version of the plugin.");
        FishingGear g = gear(p);
        if (!g.activate(i)) return Result.no("No such preset.");
        save(p, true);
        return Result.yes("Using " + g.activePreset().name() + " from your next cast.");
    }

    public Result rename(Player p, int i, String name) {
        if (!writable(p)) return Result.no("Your fishing equipment was saved by a newer version of the plugin.");
        if (!gear(p).rename(i, name)) return Result.no("A name is 1-20 letters, digits, spaces or ' & . ! -");
        save(p, true);
        return Result.yes("Preset " + (i + 1) + " is now " + name.trim() + ".");
    }

    /** The next chat line {@code p} sends (within 30 s) renames preset {@code i}; "cancel" stops it. */
    public void askRename(Player p, int i) {
        renaming.put(p.getUniqueId(), new long[]{i, System.currentTimeMillis() + 30_000});
        p.sendMessage(ChatColor.AQUA + "Type a new name for preset " + (i + 1) + " in chat (1-20 characters), or \"cancel\".");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        long[] r = renaming.get(e.getPlayer().getUniqueId());
        if (r == null) return;
        renaming.remove(e.getPlayer().getUniqueId());
        if (System.currentTimeMillis() > r[1]) return; // too late: an ordinary chat line
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Player p = e.getPlayer();
        p.getScheduler().run(plugin, task -> {
            if (text.equalsIgnoreCase("cancel")) { p.sendMessage(ChatColor.GRAY + "Rename cancelled."); return; }
            Result res = rename(p, (int) r[0], text);
            p.sendMessage((res.ok() ? ChatColor.GREEN : ChatColor.RED) + res.message());
        }, null);
    }

    public Result autoReel(Player p, boolean on) {
        if (!writable(p)) return Result.no("Your fishing equipment was saved by a newer version of the plugin.");
        gear(p).autoReel(on);
        save(p, false);
        if (!on) return Result.yes("Auto-hook reel off from your next cast.");
        if (rules().hasAutoReel(active(p))) return Result.yes("Auto-hook reel on from your next cast.");
        TackleRules.Category reel = rules().category("reel");
        return Result.yes("Auto-hook reel on, but it does nothing until the Auto-hook Reel is fitted in your active setup"
                + (reel == null ? "." : " (" + reel.name() + " opens at Fishing " + reel.unlock() + ")."));
    }

    public Result restock(Player p, boolean on) {
        if (!writable(p)) return Result.no("Your fishing equipment was saved by a newer version of the plugin.");
        gear(p).restock(on);
        save(p, false);
        return Result.yes("Restocking " + (on ? "on: when a slot runs out, the same bait comes from your inventory." : "off."));
    }

    /** Move a bait from the inventory into bag slot {@code slot} (that bait, up to a full slot). */
    public Result load(Player p, int slot, String baitId) {
        if (!writable(p)) return Result.no("Your fishing equipment was saved by a newer version of the plugin.");
        TackleRules t = rules();
        TackleRules.Bait b = t.baitById(baitId);
        if (b == null) return Result.no("That bait isn't on this server.");
        if (slot < 0 || slot >= t.bagSlots(level(p))) return Result.no("That slot opens at Fishing " + t.bagUnlock(slot) + ".");
        FishingGear g = gear(p);
        FishingGear.Slot s = g.slot(slot);
        if (!s.empty() && !s.bait().equals(baitId)) return Result.no("That slot holds another bait: take it out first.");
        int room = t.bagStack() - (s.empty() ? 0 : s.count());
        if (room <= 0) return Result.no("That slot is full.");
        int took = takeFromInventory(p.getInventory(), b.item(), room);
        if (took <= 0) return Result.no("You carry no " + b.name() + ".");
        int in = g.load(slot, baitId, took, t.bagStack());
        if (in < took) {
            GatheringCatalog.ItemDef def = catalog.get().item(b.item());
            if (def != null) p.getInventory().addItem(GearItems.makeItem(def, took - in));
        }
        depletedTold.remove(p.getUniqueId());
        save(p, false);
        return Result.yes("Put " + in + " " + b.name() + " into slot " + (slot + 1) + ".");
    }

    /** Take a slot's bait back into the inventory: only what fits; the rest stays in the bag. */
    public Result unload(Player p, int slot) {
        if (!writable(p)) return Result.no("Your fishing equipment was saved by a newer version of the plugin.");
        FishingGear g = gear(p);
        FishingGear.Slot s = g.slot(slot);
        if (s.empty()) return Result.no("That slot is empty.");
        TackleRules.Bait b = rules().baitById(s.bait());
        GatheringCatalog.ItemDef def = b == null ? null : catalog.get().item(b.item());
        if (def == null) return Result.no("That bait isn't on this server any more: it stays in the bag.");
        int room = room(p.getInventory(), GearItems.makeItem(def, 1));
        int n = Math.min(room, s.count());
        if (n <= 0) return Result.no("Your inventory is full.");
        g.take(slot, n);
        int left = n;
        while (left > 0) {
            int stack = Math.min(left, def.material().getMaxStackSize());
            p.getInventory().addItem(GearItems.makeItem(def, stack));
            left -= stack;
        }
        save(p, false);
        return Result.yes("Took " + n + " " + def.name() + " out" + (n < s.count() ? " (the rest didn't fit)." : "."));
    }

    /** How many of {@code like} the inventory's storage can still take. */
    static int room(PlayerInventory inv, ItemStack like) {
        int n = 0, max = like.getMaxStackSize();
        for (ItemStack it : inv.getStorageContents()) {
            if (it == null || it.getType().isAir()) n += max;
            else if (it.isSimilar(like)) n += Math.max(0, max - it.getAmount());
        }
        return n;
    }

    public Result swapSlots(Player p, int a, int b) {
        if (!writable(p)) return Result.no("Your fishing equipment was saved by a newer version of the plugin.");
        gear(p).swap(a, b);
        save(p, false);
        return Result.yes("Slots " + (a + 1) + " and " + (b + 1) + " swapped.");
    }

    public Result useSlot(Player p, int preset, int slot, boolean use) {
        if (!writable(p)) return Result.no("Your fishing equipment was saved by a newer version of the plugin.");
        FishingGear g = gear(p);
        g.useSlot(preset, slot, use);
        save(p, false);
        return Result.yes(g.preset(preset).name() + (use ? " uses" : " skips") + " slot " + (slot + 1) + ".");
    }

    // ── old upgraded rods (docs/design/FISHING_EQUIPMENT.md §6) ──

    /** What converting a rod would do with one of its upgrades. */
    public enum Fate { BECOMES_PART, ITEM_BACK, STAYS }

    /** One upgrade of the rod and its fate; {@code note} says why (a locked category, a removed entry). */
    public record Line(String upgrade, String name, Fate fate, String part, String note) {}

    /** The preview of converting {@code rod}: one line per upgrade, in the order they were made. */
    public List<Line> conversion(Player p, ItemStack rod) {
        List<Line> out = new ArrayList<>();
        if (rod == null || rod.getType() != Material.FISHING_ROD) return out;
        GatheringCatalog c = catalog.get();
        TackleRules t = c.tackle();
        FishingGear g = gear(p);
        int level = level(p);
        Set<String> owning = new LinkedHashSet<>(g.owned());
        for (String up : GearItems.upgrades(rod)) {
            GatheringCatalog.UpgradeDef u = c.upgrade(up);
            String partId = t.partOfUpgrade(up);
            TackleRules.Part part = t.part(partId);
            String name = u != null ? u.name() : up;
            if (part == null) { out.add(new Line(up, name, Fate.STAYS, null, "not a setup part on this server: it stays on the rod")); continue; }
            if (owning.contains(partId)) {
                GatheringCatalog.ItemDef def = c.item(part.item());
                if (def == null) { out.add(new Line(up, name, Fate.STAYS, partId, "you own it, and its item isn't on this server: it stays on the rod")); continue; }
                out.add(new Line(up, name, Fate.ITEM_BACK, partId, "you already own it: 1 " + def.name() + " comes back"));
                continue;
            }
            owning.add(partId);
            TackleRules.Category cat = t.category(part.category());
            String note = cat != null && level < cat.unlock()
                    ? "yours; " + cat.name() + " opens at Fishing " + cat.unlock() + " (the rod used it at any level)" : "becomes your part";
            out.add(new Line(up, name, Fate.BECOMES_PART, partId, note));
        }
        return out;
    }

    /**
     * Convert the rod in the main hand, exactly as {@code expected} (the preview the player confirmed) says: parts become
     * owned, items for parts already owned come back, the rod keeps everything but those upgrades. One step on the
     * player's thread; refused with nothing changed when the rod changed or the returned items don't fit.
     */
    public Result convert(Player p, ItemStack previewed, List<Line> expected) {
        if (!writable(p)) return Result.no("Your fishing equipment was saved by a newer version of the plugin.");
        PlayerInventory inv = p.getInventory();
        ItemStack rod = inv.getItemInMainHand();
        if (rod.getType() != Material.FISHING_ROD || previewed == null || !rod.isSimilar(previewed) || rod.getAmount() != 1) {
            return Result.no("The rod in your hand changed since the preview: nothing was converted. Open it again.");
        }
        List<Line> now = conversion(p, rod);
        if (!now.equals(expected)) return Result.no("Something changed since the preview: nothing was converted. Open it again.");
        GatheringCatalog c = catalog.get();
        TackleRules t = c.tackle();
        List<ItemStack> back = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        for (Line l : now) {
            if (l.fate() == Fate.STAYS) continue;
            removed.add(l.upgrade());
            if (l.fate() == Fate.ITEM_BACK) back.add(GearItems.makeItem(c.item(t.part(l.part()).item()), 1));
        }
        if (removed.isEmpty()) return Result.no("Nothing on this rod becomes a part here.");
        if (!fits(inv, back)) return Result.no("Make room for " + back.size() + " item" + (back.size() == 1 ? "" : "s") + " first: nothing was converted.");
        ItemStack plain = GearItems.withoutUpgrades(rod, removed, c);
        FishingGear g = gear(p);
        for (Line l : now) if (l.fate() == Fate.BECOMES_PART && g.own(l.part())) installDefaults(g, t, l.part());
        inv.setItemInMainHand(plain);
        for (ItemStack it : back) inv.addItem(it);
        conversions.increment();
        save(p, true);
        equipment.capture(p);
        plugin.getLogger().info("[Fishing] " + p.getName() + " converted a rod: " + removed);
        return Result.yes("Converted: " + removed.size() + " upgrade" + (removed.size() == 1 ? "" : "s") + " moved to your setup"
                + (back.isEmpty() ? "." : ", " + back.size() + " item" + (back.size() == 1 ? "" : "s") + " back for parts you already had."));
    }

    /** Whether {@code items} all fit the inventory's storage (simulated on copies; nothing moves). */
    static boolean fits(PlayerInventory inv, List<ItemStack> items) {
        ItemStack[] slots = inv.getStorageContents();
        ItemStack[] sim = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) sim[i] = slots[i] == null ? null : slots[i].clone();
        for (ItemStack it : items) {
            int left = it.getAmount();
            for (int i = 0; i < sim.length && left > 0; i++) {
                if (sim[i] != null && !sim[i].getType().isAir() && sim[i].isSimilar(it)) {
                    int add = Math.min(left, sim[i].getMaxStackSize() - sim[i].getAmount());
                    if (add > 0) { sim[i].setAmount(sim[i].getAmount() + add); left -= add; }
                }
            }
            for (int i = 0; i < sim.length && left > 0; i++) {
                if (sim[i] == null || sim[i].getType().isAir()) {
                    int add = Math.min(left, it.getMaxStackSize());
                    sim[i] = it.clone();
                    sim[i].setAmount(add);
                    left -= add;
                }
            }
            if (left > 0) return false;
        }
        return true;
    }

    // ── bait and reel recipes (crafting table) ───────────────────

    /** (Re)register the recipes in {@code tackle.recipes}. Startup or reload (the global thread); unchanged recipes stay. */
    public void registerRecipes(GatheringCatalog c) {
        StringBuilder sig = new StringBuilder();
        for (TackleRules.Recipe r : c.tackle().recipes().values()) sig.append(r).append(';');
        if (sig.toString().equals(registeredRecipes)) return;
        for (NamespacedKey k : recipeKeys) Bukkit.removeRecipe(k);
        recipeKeys.clear();
        Set<Material> locked = EnumSet.noneOf(Material.class);
        for (TackleRules.Recipe r : c.tackle().recipes().values()) {
            GatheringCatalog.ItemDef def = c.item(r.result());
            if (def == null) continue;
            if (r.unlockedBy() != null) locked.add(GearItems.makeItem(def, 1).getType());
            if (r.ingredients().stream().flatMap(List::stream).anyMatch(m -> !m.isItem())) { // a block such as water (the server's registry knows)
                plugin.getLogger().warning("[Fishing] resources.yml tackle.recipes." + r.id() + ": an ingredient is not an item - recipe left out");
                continue;
            }
            NamespacedKey key = new NamespacedKey(plugin, "tackle_" + r.id());
            ShapelessRecipe sr = new ShapelessRecipe(key, GearItems.makeItem(def, r.makes()));
            for (List<Material> any : r.ingredients()) sr.addIngredient(new RecipeChoice.MaterialChoice(any));
            if (Bukkit.addRecipe(sr)) recipeKeys.add(key);
        }
        lockedResults = locked;
        registeredRecipes = sig.toString();
    }

    /** The recipe a key is, when it is one of ours. */
    private TackleRules.Recipe recipeOf(org.bukkit.inventory.Recipe r) {
        if (!(r instanceof Keyed k) || !recipeKeys.contains(k.getKey())) return null;
        return rules().recipes().get(k.getKey().getKey().substring("tackle_".length()));
    }

    /** True when {@code p} may craft {@code r} (an unlocked recipe needs its collection reward). */
    public boolean mayCraft(Player p, TackleRules.Recipe r) {
        if (r == null || r.unlockedBy() == null) return true;
        Fishing f = plugin.getFishing();
        return f != null && f.journal(p).granted(r.unlockedBy());
    }

    /** The recipe keys {@code p} should see in the recipe book (everyone's, and the unlocked ones they earned). */
    public void discoverRecipes(Player p) {
        List<NamespacedKey> keys = new ArrayList<>();
        for (TackleRules.Recipe r : rules().recipes().values()) {
            NamespacedKey key = new NamespacedKey(plugin, "tackle_" + r.id());
            if (recipeKeys.contains(key) && mayCraft(p, r)) keys.add(key);
        }
        if (!keys.isEmpty()) p.discoverRecipes(keys);
    }

    /** When a player was last told rod parts are fitted in Equipment now (at most once a minute). */
    private final Map<UUID, Long> craftHint = new ConcurrentHashMap<>();

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepare(PrepareItemCraftEvent e) {
        // Every change to any crafting grid comes here, and getRecipe() builds a whole recipe (docs/PERFORMANCE.md): the
        // result vanilla already worked out decides first. A locked recipe makes one of its results; the old rod upgrade
        // makes nothing.
        ItemStack shown = e.getInventory().getResult();
        if (shown != null && !shown.getType().isAir()) {
            if (!lockedResults.contains(shown.getType())) return;
            TackleRules.Recipe r = recipeOf(e.getRecipe());
            if (r != null && r.unlockedBy() != null && (!(e.getView().getPlayer() instanceof Player p) || !mayCraft(p, r))) {
                e.getInventory().setResult(null);
            }
            return;
        }
        // A rod and a part's item in the grid: the old way of upgrading a rod. Say where it is done now.
        if (rules().enabled() && e.getView().getPlayer() instanceof Player p) {
            ItemStack[] grid = e.getInventory().getMatrix();
            boolean rod = false;
            String part = null;
            for (ItemStack it : grid) {
                if (it == null) continue;
                if (it.getType() == Material.FISHING_ROD) rod = true;
                String id = GearItems.itemId(it);
                if (id != null) for (TackleRules.Part pt : rules().parts().values()) if (id.equals(pt.item())) part = pt.name();
            }
            if (!rod || part == null) return;
            long now = System.currentTimeMillis();
            Long last = craftHint.get(p.getUniqueId());
            if (last != null && now - last < 60_000) return;
            craftHint.put(p.getUniqueId(), now);
            p.sendMessage(ChatColor.AQUA + "Rod parts are fitted in Player Menu → Skills → Fishing → Equipment now: the " + part
                    + " goes on your character and works with any rod.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraft(CraftItemEvent e) {
        TackleRules.Recipe r = recipeOf(e.getRecipe());
        if (r == null || r.unlockedBy() == null) return;
        if (!(e.getWhoClicked() instanceof Player p) || !mayCraft(p, r)) e.setCancelled(true);
    }

    /** A crafter has no owner to have unlocked a recipe: it makes only the ones open to everyone. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafter(CrafterCraftEvent e) {
        TackleRules.Recipe r = recipeOf(e.getRecipe());
        if (r != null && r.unlockedBy() != null) e.setCancelled(true);
    }

    // ── collection milestones (docs/design/FISHING_EQUIPMENT.md §10) ──

    private final LongAdder rewardsGranted = new LongAdder();

    /**
     * Every milestone {@code p} meets now and hasn't been granted: granted once (remembered in the journal), its unlocks
     * applied (a part owned and put where it suits, a recipe in the recipe book, a title shown) and celebrated. A lost
     * record would grant the same unlocks again, which are sets, not stacks. Player's thread; cheap (a few counts).
     */
    public List<TackleRules.Reward> checkRewards(Player p) {
        Fishing f = plugin.getFishing();
        TackleRules t = rules();
        if (f == null || !t.enabled() || t.rewards().isEmpty() || !writable(p)) return List.of();
        FishingJournal j = f.journal(p);
        if (j.readOnly()) return List.of(); // a journal that can't be saved grants nothing it couldn't remember
        FishingRules fr = catalog.get().fishing();
        List<TackleRules.Reward> now = new ArrayList<>();
        boolean setup = false, recipes = false;
        for (TackleRules.Reward r : t.rewards().values()) {
            if (j.granted(r.id()) || !TackleRules.progress(r, fr, j.species().keySet(), j.bestsSet()).done()) continue;
            if (!j.grant(r.id())) continue;
            now.add(r);
            for (String u : r.unlocks()) {
                if (u.startsWith("part:")) {
                    String id = u.substring(5);
                    FishingGear g = gear(p);
                    if (g.own(id)) installDefaults(g, t, id);
                    setup = true;
                } else if (u.startsWith("recipe:")) recipes = true;
            }
        }
        if (now.isEmpty()) return now;
        f.saveJournal(p);
        if (setup) save(p, true);
        if (recipes) discoverRecipes(p);
        rewardsGranted.add(now.size());
        Skills.Celebrations cel = plugin.getCelebrations();
        for (TackleRules.Reward r : now) {
            Skills.Celebration c = celebration(r, t);
            if (cel != null) cel.celebrate(p, c);
            else for (String line : c.chat()) p.sendMessage(line);
        }
        plugin.getLogger().info("[Fishing] " + p.getName() + " reached " + now.stream().map(TackleRules.Reward::id).toList());
        return now;
    }

    /** A milestone shown: what it asked and what it opened (a title is a major moment). */
    Skills.Celebration celebration(TackleRules.Reward r, TackleRules t) {
        List<String> opened = new ArrayList<>();
        boolean title = false;
        for (String u : r.unlocks()) {
            String what = unlockText(u, t);
            if (what != null) opened.add(what);
            if (u.startsWith("title:")) title = true;
        }
        String unlocked = String.join("§7, §f", opened);
        String hud = "§6✦ " + r.name() + " §7· " + TackleRules.asks(r) + (opened.isEmpty() ? "" : " · §f" + unlocked);
        List<String> chat = List.of("§6✦ Fishing milestone: " + r.name() + "§7 (" + TackleRules.asks(r) + ")."
                + (opened.isEmpty() ? "" : " Unlocked: §f" + unlocked + "§7."));
        return new Skills.Celebration(Skills.Celebration.Kind.COLLECTION, title ? Skills.Celebration.Tier.MAJOR : Skills.Celebration.Tier.ORDINARY,
                hud, "§6✦ " + r.name(), title ? "§6" + r.name() : null, title ? "§7" + TackleRules.asks(r) + " · your journal shows it" : null, chat, -1);
    }

    /** One unlock, plainly: "Surveyor's Float (Equipment → Lure)", "the Wayfarer's Bait recipe", "the Master Angler emblem". */
    public String unlockText(String unlock, TackleRules t) {
        if (unlock.startsWith("part:")) {
            TackleRules.Part part = t.part(unlock.substring(5));
            if (part == null) return null;
            TackleRules.Category c = t.category(part.category());
            return part.name() + " (Equipment → " + (c == null ? part.category() : c.name()) + ")";
        }
        if (unlock.startsWith("recipe:")) {
            TackleRules.Recipe r = t.recipes().get(unlock.substring(7));
            GatheringCatalog.ItemDef def = r == null ? null : catalog.get().item(r.result());
            return def == null ? null : "the " + def.name() + " recipe (crafting table)";
        }
        if (unlock.startsWith("title:")) {
            TackleRules.Reward r = t.rewardForTitle(unlock.substring(6));
            return "the " + (r == null ? unlock.substring(6) : r.name()) + " emblem";
        }
        return null;
    }

    /** True when {@code j}'s granted milestones show title {@code id} (the Master Angler emblem). */
    public boolean hasTitle(FishingJournal j, String id) {
        return rules().titles(j.grantedRewards()).contains(id);
    }

    // ── specialists (docs/design/FISHING_EQUIPMENT.md §7) ─────────

    /**
     * The Surveyor's Float's note for where {@code p} fishes (their bobber, or the water they look at): null when it isn't
     * in use or the place isn't known, else whether anything they haven't caught can bite there now.
     */
    public String surveyorNote(Player p) {
        TackleRules.Part sp = rules().specialist(active(p));
        Fishing f = plugin.getFishing();
        if (sp == null || sp.special() != TackleRules.Special.DISCOVERY || f == null) return null;
        Fishing.Place place = f.place(p);
        if (place.source() == Fishing.PlaceSource.UNKNOWN || place.moment().water() == null) return null;
        FishingRules fr = catalog.get().fishing();
        FishingJournal j = f.journal(p);
        int level = level(p);
        long fresh = fr.eligible(place.moment(), level).stream().filter(s -> !j.caught(s.id())).count();
        FishingRules.Water w = fr.water(place.moment().water());
        String where = w == null ? place.moment().water() : w.name();
        String preview = place.source() == Fishing.PlaceSource.LOOKING ? " §8(preview: the water you look at)" : "";
        return (fresh == 0 ? "§7Nothing new can bite in " + where + " now: normal odds"
                : "§d" + fresh + " species you haven't caught can bite in " + where + " now") + preview;
    }

    /**
     * The species factor {@code p}'s active specialist gives each species (Surveyor's Float on the species they haven't
     * caught; bait is held per cast and left out), for /cerb fish info. Player's thread.
     */
    public java.util.function.ToDoubleFunction<FishingRules.Species> infoFactor(Player p) {
        TackleRules t = rules();
        TackleRules.Part sp = t.specialist(active(p));
        Fishing f = plugin.getFishing();
        if (sp == null || sp.special() != TackleRules.Special.DISCOVERY || f == null) return s -> 1.0;
        Set<String> caught = Set.copyOf(f.journal(p).species().keySet());
        return s -> t.weightFactor(s, caught.contains(s.id()), sp.value(), 1.0);
    }

    // ── players come and go ──────────────────────────────────────

    /** A bait still held at join was on a cast that ended with the server (a restart or a crash): it goes back. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        FishingGear g = gear(p);
        if (g.held() != null && writable(p)) {
            giveBack(p, g, g.release(null, rules().bagSlots(level(p))));
            save(p, false);
        }
        discoverRecipes(p);
        // A milestone met before this version (or while it was off) is granted now, once, when the player can see it.
        p.getScheduler().runDelayed(plugin, task -> { if (p.isOnline()) checkRewards(p); }, null, 40L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        castEnded(e.getPlayer(), null); // the cast ends with the player: its bait goes back, saved as they leave
        gear.remove(id);
        views.remove(id);
        unreadable.remove(id);
        depletedTold.remove(id);
        renaming.remove(id);
        craftHint.remove(id);
    }

    // ── admin ────────────────────────────────────────────────────

    public List<String> stats() {
        return List.of("bait held " + holds.sum() + ", used " + used.sum() + ", put back " + putBack.sum() + ", restocked " + restocked.sum()
                + " · parts fitted " + fits.sum() + " · rods converted " + conversions.sum() + " · milestones granted " + rewardsGranted.sum()
                + " · equipment loaded " + gear.size());
    }

    /** A player's equipment as text (admin dump). Player's thread. */
    public List<String> describe(Player p) {
        FishingGear g = gear(p);
        List<String> out = new ArrayList<>();
        out.add("owned: " + g.owned() + (unreadable.contains(p.getUniqueId()) ? " (UNREADABLE: kept, read-only)" : ""));
        for (int i = 0; i < FishingGear.PRESETS; i++) {
            FishingGear.Preset pr = g.preset(i);
            StringBuilder s = new StringBuilder();
            for (int k = 0; k < FishingGear.SLOTS; k++) s.append(pr.usesSlot(k) ? '1' : '0');
            out.add((i == g.activeIndex() ? "▶ " : "  ") + (i + 1) + " " + pr.name() + ": " + pr.parts() + " bait slots " + s);
        }
        Map<Integer, String> bag = new LinkedHashMap<>();
        for (int i = 0; i < FishingGear.SLOTS; i++) bag.put(i + 1, g.slot(i).empty() ? "-" : g.slot(i).bait() + " ×" + g.slot(i).count());
        out.add("bag: " + bag + " · held: " + (g.held() == null ? "none" : g.held().bait() + " (slot " + (g.held().slot() + 1) + ")")
                + " · reel " + (g.autoReel() ? "on" : "off") + " · restock " + (g.restock() ? "on" : "off"));
        out.add("active now: " + active(p) + " at Fishing " + level(p));
        return out;
    }

    /** For the admin: put {@code n} of a bait into a player's bag (the first slot that takes it). */
    public int adminBait(Player p, String baitId, int n) {
        TackleRules.Bait b = rules().baitById(baitId);
        if (b == null || !writable(p)) return 0;
        FishingGear g = gear(p);
        int in = 0;
        for (int i = 0; i < rules().bagSlots(level(p)) && in < n; i++) in += g.load(i, baitId, n - in, rules().bagStack());
        if (in > 0) depletedTold.remove(p.getUniqueId());
        save(p, false);
        return in;
    }

    /** For the admin: take a part away (out of every preset). */
    public boolean adminDisown(Player p, String partId) {
        if (!writable(p)) return false;
        boolean had = gear(p).disown(partId);
        if (had) save(p, true);
        return had;
    }

    static String plainName(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}

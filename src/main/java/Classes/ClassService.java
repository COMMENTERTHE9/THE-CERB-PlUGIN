package Classes;

import Attributes.AttributeRules;
import Attributes.AttributeService;
import Attributes.AttributeSource;
import Attributes.Contribution;
import Manager.PvpTag;
import Skills.SkillManager;
import cerberus.world.cerb.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Players' classes at runtime (docs/design/CLASSES.md): loading and saving profiles, learning
 * classes when skills meet their entry, class XP and ranks, loadouts and the switching rules, and
 * the attribute contributions of equipped passives and running buffs.
 *
 * <p>Folia: a profile is only touched on its player's region thread. The attribute engine may ask
 * for contributions from any thread, so they live in a separate thread-safe copy, rebuilt on the
 * player's thread whenever the loadout or a buff changes. Combat times and the Riposte / Feint
 * windows are read from damage events on other threads, so they live in concurrent maps too.
 */
public final class ClassService implements AttributeSource {

    /** No loadout change within this long after a hit dealt or taken (or while PvP-tagged). */
    public static final long COMBAT_MS = 8_000;
    /** Gap between two loadout switches. */
    public static final long SWITCH_COOLDOWN_MS = 10_000;
    /** No casting for this long after a switch. */
    public static final long CAST_LOCK_AFTER_SWITCH_MS = 1_000;
    /** Share of a kill's XP that goes to the classes you fought with (split between them), and how recent counts. */
    public static final double KILL_SHARE = 0.5;
    public static final long KILL_WINDOW_MS = 10_000;
    /** Class XP for a cast that did something. */
    public static final double CAST_XP = 6;

    public enum Slot { ACTIVE, PASSIVE, KEYSTONE }

    /** A running buff from an ability: ends at {@code until} or when the ability leaves the loadout. */
    private record Running(String abilityId, Contribution contribution, long until) {}

    /** An open Riposte window. */
    record CounterWindow(String abilityId, long until, double coefficient, long successCooldownMs) {}

    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final SkillManager skills;
    private final AttributeService attributes;
    private final Supplier<PvpTag> pvp;
    private volatile ClassCatalog catalog;

    private final Map<UUID, ClassProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, List<Running>> buffs = new ConcurrentHashMap<>();
    private final Map<UUID, List<Contribution>> contributions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCombat = new ConcurrentHashMap<>();
    final Map<UUID, CounterWindow> counters = new ConcurrentHashMap<>();
    final Map<UUID, Long> empowered = new ConcurrentHashMap<>();
    /** Final saves on quit: written in order on one thread, drained on shutdown (never throttled away). */
    private final java.util.concurrent.ExecutorService writer = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Cerberus-Classes-DB");
        t.setDaemon(true);
        return t;
    });
    /** Pushes changed attributes into the game (max health, defense, mana, speeds). Player's thread. */
    private volatile java.util.function.Consumer<Player> attributesChanged = p -> { };

    public ClassService(JavaPlugin plugin, DatabaseManager db, SkillManager skills,
                        AttributeService attributes, Supplier<PvpTag> pvp, ClassCatalog catalog) {
        this.plugin = plugin;
        this.db = db;
        this.skills = skills;
        this.attributes = attributes;
        this.pvp = pvp;
        this.catalog = catalog;
    }

    /** What to run after this player's class contributions changed (Manager.AttributeEffects#refresh). */
    public void setAttributesChanged(java.util.function.Consumer<Player> hook) {
        this.attributesChanged = hook == null ? p -> { } : hook;
    }

    public ClassCatalog catalog() { return catalog; }

    /** The player's profile, or null until it has loaded (or when the load failed). Player's thread. */
    public ClassProfile profile(UUID player) { return profiles.get(player); }

    // ── lifecycle ───────────────────────────────────────────────

    /**
     * Joined: read the saved profile off the tick, then set it up on the player's thread. With no
     * classes configured (missing or broken classes.yml) nothing loads, so nothing is ever saved over.
     */
    public void load(Player p) {
        if (catalog.classes().isEmpty()) return;
        UUID id = p.getUniqueId();
        CompletableFuture<Optional<DatabaseManager.ClassData>> read;
        try {
            // On the writer thread, behind any save still queued (a quick rejoin must read the quit save).
            read = CompletableFuture.supplyAsync(() -> {
                try {
                    return Optional.ofNullable(db.loadClassData(id));
                } catch (SQLException e) {
                    throw new CompletionException(e);
                }
            }, writer);
        } catch (java.util.concurrent.RejectedExecutionException e) {
            return; // shutting down
        }
        read.whenComplete((data, err) -> p.getScheduler().run(plugin, t -> {
            if (!p.isOnline() || profiles.containsKey(id)) return; // left meanwhile, or already loaded
            if (err != null) {
                // Never fall back to an empty profile: its first save would overwrite the stored one.
                plugin.getLogger().severe("[Classes] Could not load " + p.getName() + "'s classes (" + err.getMessage()
                        + "). Classes stay off for this session; nothing was overwritten.");
                return;
            }
            List<String> repairs = new ArrayList<>();
            DatabaseManager.ClassData row = data.orElse(null);
            ClassProfile prof = ClassProfile.fromYaml(row == null ? null : row.data(), catalog, System.currentTimeMillis(), repairs);
            if (prof == null) {
                // Unreadable, or written by a newer version: keep it exactly as stored and stay off.
                plugin.getLogger().severe("[Classes] Could not read " + p.getName() + "'s saved classes (" + String.join("; ", repairs)
                        + "). Classes stay off for this session; the stored data was not changed.");
                return;
            }
            if (row != null) prof.stampAfter(row.stamp());
            if (!repairs.isEmpty()) {
                plugin.getLogger().warning("[Classes] Repaired " + p.getName() + "'s saved classes: " + String.join("; ", repairs));
            }
            profiles.put(id, prof);
            learnEligible(p, prof);
            refreshContributions(p);
            if (!repairs.isEmpty()) save(p);
        }, null));
    }

    /** Leaving: write the profile now (in order, not throttled), then forget everything. Player's thread (quit event). */
    public void unload(Player p) {
        UUID id = p.getUniqueId();
        ClassProfile prof = profiles.remove(id);
        if (prof != null) {
            long now = System.currentTimeMillis();
            write(id, prof.snapshot(now), prof.nextStamp(now));
        }
        saveQueued.remove(id);
        forgetRuntime(id);
    }

    /**
     * Plugin disable: write every loaded profile right here (async tasks don't outlive a disable),
     * then let the queued quit saves finish.
     */
    public void shutdown() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, ClassProfile> e : profiles.entrySet()) {
            ClassProfile prof = e.getValue();
            db.saveClassData(e.getKey(), ClassProfile.VERSION, prof.toYaml(now), prof.nextStamp(now));
        }
        profiles.clear();
        for (UUID id : List.copyOf(contributions.keySet())) forgetRuntime(id);
        writer.shutdown();
        try {
            if (!writer.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                plugin.getLogger().warning("[Classes] Quit saves did not finish within 5 s.");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void forgetRuntime(UUID id) {
        buffs.remove(id);
        contributions.remove(id);
        lastCombat.remove(id);
        counters.remove(id);
        empowered.remove(id);
    }

    /** Died: every temporary effect ends (buffs, Riposte, Feint, Momentum). Player's thread. */
    public void onDeath(Player p) {
        UUID id = p.getUniqueId();
        buffs.remove(id);
        counters.remove(id);
        empowered.remove(id);
        ClassProfile prof = profiles.get(id);
        if (prof != null) prof.momentum = 0;
        refreshContributions(p);
    }

    /** A new catalog (reload): every loaded profile is reconciled with it. One with no classes is refused. */
    public void setCatalog(ClassCatalog next) {
        if (next.classes().isEmpty()) {
            plugin.getLogger().warning("[Classes] The reloaded classes.yml has no classes; keeping the previous ones.");
            return;
        }
        boolean wasOff = catalog.classes().isEmpty();
        this.catalog = next;
        if (wasOff) for (Player online : Bukkit.getOnlinePlayers()) load(online); // classes were off: load everyone now
        for (UUID id : profiles.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) continue;
            p.getScheduler().run(plugin, t -> {
                ClassProfile prof = profiles.get(id);
                if (prof == null) return;
                List<String> repairs = new ArrayList<>();
                prof.reconcile(next, repairs);
                if (!repairs.isEmpty()) plugin.getLogger().info("[Classes] After reload, " + p.getName() + ": " + String.join("; ", repairs));
                endEffectsNotIn(p, prof.activeLoadout());
                learnEligible(p, prof);
                refreshContributions(p);
                save(p);
            }, null);
        }
    }

    /**
     * Save soon: at most one snapshot per player per second, copied on the player's thread and
     * turned into text and written on the same single writer thread as loads and quit saves, so the
     * database sees them in order and the tick never builds the document. Quit and
     * shutdown write at once ({@link #unload}, {@link #shutdown}); a pending save of a player who
     * left is dropped by the scheduler, after the quit save already wrote everything.
     */
    public void save(Player p) {
        UUID id = p.getUniqueId();
        if (!profiles.containsKey(id) || !saveQueued.add(id)) return; // not loaded, or a save is already on its way
        var task = p.getScheduler().runDelayed(plugin, t -> {
            saveQueued.remove(id);
            ClassProfile prof = profiles.get(id);
            if (prof == null) return;
            long now = System.currentTimeMillis();
            write(id, prof.snapshot(now), prof.nextStamp(now)); // a cheap copy here; the text is built on the writer
        }, () -> saveQueued.remove(id), SAVE_DELAY_TICKS);
        if (task == null) saveQueued.remove(id); // leaving: the quit save covers it
    }

    /** One player's save waiting to be taken (see {@link #save}). */
    private final java.util.Set<UUID> saveQueued = ConcurrentHashMap.newKeySet();
    private static final long SAVE_DELAY_TICKS = 20;

    private void write(UUID id, ClassProfile.Snapshot snap, long stamp) {
        try {
            writer.execute(() -> db.saveClassData(id, ClassProfile.VERSION, snap.toYaml(), stamp));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            db.saveClassData(id, ClassProfile.VERSION, snap.toYaml(), stamp); // shutting down: write here
        }
    }

    // ── learning and ranks ──────────────────────────────────────

    /** Skill levels changed (any thread): open any class the player now qualifies for. */
    public void skillsChanged(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p == null) return;
        p.getScheduler().run(plugin, t -> {
            ClassProfile prof = profiles.get(id);
            if (prof != null) learnEligible(p, prof);
        }, null);
    }

    private void learnEligible(Player p, ClassProfile prof) {
        UUID id = p.getUniqueId();
        if (!skills.hasLoadedSkills(id)) return;
        boolean any = false;
        for (ClassDef c : catalog.classes().values()) {
            if (prof.knows(c.id()) || !c.canLearn(s -> skills.getSkillLevel(id, s))) continue;
            prof.learn(c.id());
            any = true;
            p.sendMessage(ChatColor.GOLD + "✦ New class: " + ChatColor.YELLOW + c.displayName() + ChatColor.GRAY + " (" + c.role()
                    + "). Your skills opened it. " + ChatColor.DARK_GRAY + "/class");
        }
        if (any) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.4f, 1.4f);
            save(p);
        }
    }

    /** Add class XP (player's thread); announces ranks and new unlocks. */
    public void awardXp(Player p, String classId, double xp) {
        ClassProfile prof = profiles.get(p.getUniqueId());
        if (prof == null) return;
        int before = prof.rank(classId);
        ClassRanks.Result r = prof.award(classId, xp);
        if (r == null) return;
        if (r.ranksGained() > 0) announceRanks(p, classId, before, r.rank());
        save(p);
    }

    /** Admin/test: set a class to an exact rank (learning it if needed). Player's thread. */
    public void setRank(Player p, String classId, int rank) {
        ClassProfile prof = profiles.get(p.getUniqueId());
        if (prof == null || catalog.classDef(classId) == null) return;
        int before = prof.rank(classId);
        prof.setRank(classId, rank);
        if (prof.rank(classId) > before) announceRanks(p, classId, before, prof.rank(classId));
        repairAll(p, prof);
        save(p);
    }

    private void announceRanks(Player p, String classId, int from, int to) {
        ClassDef c = catalog.classDef(classId);
        if (c == null) return;
        p.sendMessage(ChatColor.GOLD + "✦ " + c.displayName() + " rank " + to);
        for (String aid : c.abilities()) {
            AbilityDef a = catalog.ability(aid);
            if (a != null && a.unlockRank() > from && a.unlockRank() <= to) {
                p.sendMessage(ChatColor.GREEN + "  Unlocked: " + ChatColor.WHITE + a.displayName() + ChatColor.DARK_GRAY + " ("
                        + a.kind().name().toLowerCase(java.util.Locale.ROOT) + ")");
            }
        }
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
    }

    /** A mob died to this player (any thread): part of its XP goes to the classes used in the fight. */
    public void onKill(Player p, double killXp) {
        if (!(killXp > 0)) return;
        Runnable share = () -> {
            ClassProfile prof = profiles.get(p.getUniqueId());
            if (prof == null) return;
            long now = System.currentTimeMillis();
            List<String> used = new ArrayList<>();
            prof.lastUse.forEach((c, at) -> { if (now - at <= KILL_WINDOW_MS) used.add(c); });
            if (used.isEmpty()) return;
            double each = killXp * KILL_SHARE / used.size();
            for (String c : used) awardXp(p, c, each);
        };
        if (Bukkit.isOwnedByCurrentRegion(p)) share.run();
        else p.getScheduler().run(plugin, t -> share.run(), null);
    }

    // ── combat state ────────────────────────────────────────────

    /** A real hit was dealt or taken (any thread). */
    public void markCombat(UUID player) { lastCombat.put(player, System.currentTimeMillis()); }

    /** True within {@link #COMBAT_MS} of a hit dealt or taken, or while PvP-tagged. */
    public boolean inCombat(UUID player) {
        Long t = lastCombat.get(player);
        PvpTag tag = pvp.get();
        return (t != null && System.currentTimeMillis() - t < COMBAT_MS) || (tag != null && tag.isTagged(player));
    }

    // ── loadouts ────────────────────────────────────────────────

    /**
     * Put {@code abilityId} (null: clear) in a slot of loadout {@code index}. Moving an ability that
     * is already in another slot of that loadout empties its old slot. Returns why not, or null
     * when done. Player's thread.
     */
    public String equip(Player p, int index, Slot slot, int pos, String abilityId) {
        ClassProfile prof = profiles.get(p.getUniqueId());
        if (prof == null) return "Your classes are still loading.";
        if (index < 0 || index >= prof.loadouts().size()) return "No such loadout.";
        if (abilityId != null && catalog.ability(abilityId) == null) return "Unknown ability: " + abilityId;
        if (index == prof.activeIndex() && inCombat(p.getUniqueId())) return "Not during a fight: wait until 8 s after the last hit.";
        // Slots that stopped being allowed (an unlock or catalog change) never block an unrelated edit.
        Loadout l = prof.repair(prof.loadouts().get(index), catalog, new ArrayList<>());
        int size = slot == Slot.ACTIVE ? Loadout.ACTIVE_SLOTS : slot == Slot.PASSIVE ? Loadout.PASSIVE_SLOTS : 1;
        if (pos < 0 || pos >= size) return "No such slot.";
        if (abilityId != null) {
            for (int i = 0; i < Loadout.ACTIVE_SLOTS; i++) if (abilityId.equals(l.actives().get(i))) l = l.withActive(i, null);
            for (int i = 0; i < Loadout.PASSIVE_SLOTS; i++) if (abilityId.equals(l.passives().get(i))) l = l.withPassive(i, null);
            if (abilityId.equals(l.keystone())) l = l.withKeystone(null);
        }
        Loadout next = switch (slot) {
            case ACTIVE -> l.withActive(pos, abilityId);
            case PASSIVE -> l.withPassive(pos, abilityId);
            case KEYSTONE -> l.withKeystone(abilityId);
        };
        List<String> problems = next.problems(catalog.abilities(), prof::unlocked);
        if (!problems.isEmpty()) return problems.get(0);
        prof.setLoadout(index, next);
        if (index == prof.activeIndex()) {
            endEffectsNotIn(p, next);
            refreshContributions(p);
        }
        save(p);
        return null;
    }

    /** Make loadout {@code index} the one in use. Returns why not, or null when done. Player's thread. */
    public String switchTo(Player p, int index) {
        ClassProfile prof = profiles.get(p.getUniqueId());
        if (prof == null) return "Your classes are still loading.";
        if (index < 0 || index >= prof.loadouts().size()) return "No such loadout.";
        if (index == prof.activeIndex()) return prof.activeLoadout().name() + " is already in use.";
        if (inCombat(p.getUniqueId())) return "Not during a fight: wait until 8 s after the last hit.";
        long now = System.currentTimeMillis();
        long wait = prof.lastSwitchAt + SWITCH_COOLDOWN_MS - now;
        if (wait > 0) return "Switching again in " + (wait + 999) / 1000 + " s.";
        prof.setActive(index);
        prof.lastSwitchAt = now;
        endEffectsNotIn(p, prof.activeLoadout());
        refreshContributions(p);
        save(p);
        return null;
    }

    /** Rename a loadout (any time). */
    public String rename(Player p, int index, String name) {
        ClassProfile prof = profiles.get(p.getUniqueId());
        if (prof == null) return "Your classes are still loading.";
        if (index < 0 || index >= prof.loadouts().size()) return "No such loadout.";
        String n = name == null ? "" : name.strip();
        if (n.isEmpty() || n.length() > 24) return "A name has 1-24 characters.";
        Loadout l = prof.loadouts().get(index);
        prof.setLoadout(index, new Loadout(n, l.actives(), l.passives(), l.keystone()));
        save(p);
        return null;
    }

    /** After an unlock change, empty any slot (in every loadout) that is no longer allowed. */
    private void repairAll(Player p, ClassProfile prof) {
        List<String> repairs = new ArrayList<>();
        for (int i = 0; i < prof.loadouts().size(); i++) prof.setLoadout(i, prof.repair(prof.loadouts().get(i), catalog, repairs));
        endEffectsNotIn(p, prof.activeLoadout());
        refreshContributions(p);
    }

    /**
     * Everything temporary that came from an ability not in {@code l} ends now: buffs, the Riposte
     * window, Feint, Momentum. Nothing from an inactive loadout keeps counting (CLASSES.md §6).
     */
    void endEffectsNotIn(Player p, Loadout l) {
        UUID id = p.getUniqueId();
        List<Running> list = buffs.get(id);
        if (list != null) list.removeIf(r -> !l.contains(r.abilityId()));
        CounterWindow c = counters.get(id);
        if (c != null && !l.contains(c.abilityId())) counters.remove(id);
        empowered.remove(id);
        ClassProfile prof = profiles.get(id);
        if (prof != null) prof.momentum = 0;
    }

    // ── buffs and attribute contributions ───────────────────────

    /** Start (or refresh: never stack) an ability's timed buff. Player's thread. */
    void addBuff(Player p, AbilityDef a, EffectSpec.Buff b) {
        UUID id = p.getUniqueId();
        long until = System.currentTimeMillis() + Math.round(b.seconds() * 1000);
        Contribution c = b.attribute() != null
                ? Contribution.attribute("class:" + a.id(), a.displayName(), b.attribute(), b.amount(), null)
                : Contribution.stat("class:" + a.id(), a.displayName(), b.stat(), b.amount(), null);
        List<Running> list = buffs.computeIfAbsent(id, k -> new ArrayList<>());
        list.removeIf(r -> r.abilityId().equals(a.id()));
        list.add(new Running(a.id(), c, until));
        refreshContributions(p);
        p.getScheduler().runDelayed(plugin, t -> {
            List<Running> now = buffs.get(id);
            if (now != null && now.removeIf(r -> r.until() <= System.currentTimeMillis())) refreshContributions(p);
        }, null, Math.max(1, Math.round(b.seconds() * 20)) + 1);
    }

    /** Rebuild this player's contributions (equipped passives and keystone, running buffs). Player's thread. */
    void refreshContributions(Player p) {
        UUID id = p.getUniqueId();
        ClassProfile prof = profiles.get(id);
        List<Contribution> out = new ArrayList<>();
        if (prof != null) {
            Loadout l = prof.activeLoadout();
            List<String> passive = new ArrayList<>(l.passives());
            passive.add(l.keystone());
            for (String aid : passive) {
                AbilityDef a = catalog.ability(aid);
                if (a == null || !prof.unlocked(a)) continue;
                for (AbilityDef.Passive ps : a.passives()) {
                    out.add(ps.attribute() != null
                            ? Contribution.attribute("class:" + a.id(), a.displayName(), ps.attribute(), ps.amount(), null)
                            : Contribution.stat("class:" + a.id(), a.displayName(), ps.stat(), ps.amount(), null));
                }
            }
            long now = System.currentTimeMillis();
            List<Running> list = buffs.get(id);
            if (list != null) for (Running r : list) if (r.until() > now && l.contains(r.abilityId())) out.add(r.contribution());
        }
        if (prof == null && !contributions.containsKey(id)) return; // not loaded (or gone): nothing to clear
        List<Contribution> before = contributions.put(id, List.copyOf(out));
        if (!out.equals(before == null ? List.of() : before)) {
            attributes.markDirty(id);
            attributesChanged.accept(p);
        }
    }

    @Override public String id() { return "class"; }

    @Override
    public List<Contribution> contributions(UUID player, AttributeRules rules) {
        return contributions.getOrDefault(player, List.of());
    }

    // ── Riposte and Feint windows (read by damage events) ───────

    void openCounter(UUID player, AbilityDef a, EffectSpec.Counter c) {
        counters.put(player, new CounterWindow(a.id(), System.currentTimeMillis() + c.windowMs(), c.coefficient(), c.successCooldownMs()));
    }

    /** Close and return the player's open counter window, if it is still open (any thread). */
    CounterWindow takeCounter(UUID player) {
        CounterWindow w = counters.remove(player);
        return w != null && w.until() >= System.currentTimeMillis() ? w : null;
    }

    void empower(UUID player, double seconds) {
        empowered.put(player, System.currentTimeMillis() + Math.round(seconds * 1000));
    }

    /** True once if the player's next weapon hit is empowered (Feint): used up by that hit. Any thread. */
    public boolean consumeEmpower(UUID player) {
        Long until = empowered.remove(player);
        return until != null && until >= System.currentTimeMillis();
    }

    // ── text ────────────────────────────────────────────────────

    /**
     * What a class's entry needs, with the player's own levels: "Blade Mastery 10 (you: 3), Critical
     * Strike 5 (you: 1)"; an any-of entry reads "Blade Mastery 5 (you: 1) or Weapon Mastery 5 (you: 1)".
     */
    public String requirements(ClassDef c, UUID player) {
        List<String> all = new ArrayList<>();
        for (Map.Entry<String, Integer> e : c.entry().entrySet()) all.add(need(e, player));
        List<String> any = new ArrayList<>();
        for (Map.Entry<String, Integer> e : c.entryAny().entrySet()) any.add(need(e, player));
        if (!any.isEmpty()) all.add(String.join(" or ", any));
        return all.isEmpty() ? "nothing" : String.join(", ", all);
    }

    private String need(Map.Entry<String, Integer> e, UUID player) {
        return Skills.SkillNames.display(e.getKey()) + " " + e.getValue() + " (you: " + skills.getSkillLevel(player, e.getKey()) + ")";
    }

    /** One passive bonus in words: "+8 Vitality", "+5% Crit chance", "-6% Attack speed". */
    public static String bonusText(AbilityDef.Passive ps) {
        if (ps.attribute() != null) {
            return (ps.amount() >= 0 ? "+" : "") + format(ps.amount()) + " " + ps.attribute().displayName;
        }
        return (ps.amount() >= 0 ? "+" : "") + format(ps.amount() * 100) + "% " + ps.stat().displayName;
    }

    private static String format(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    // ── diagnostics ─────────────────────────────────────────────

    public int loadedCount() { return profiles.size(); }
}

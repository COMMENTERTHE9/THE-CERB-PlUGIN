package Skills;

import Attributes.KillXp;
import Gathering.GatherRules;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exact XP while loading (the owner's decision on item 10), on the plugin's own rules and buffer: an action taken while a
 * player's skills load is kept as the rule it pays by and paid once the load completes, in order, at the player's restored
 * level, seeing the level-ups of the actions before it (the chain of SkillManager.whenLoaded, GatheringService.award and
 * MobKillListener: the level is read when the action runs). Each case compares a session whose load completes after its
 * actions with one already loaded doing the same, and runs the previous buffer's way too (the amount worked out when the
 * action happened, while the level still read 1, then added at the load), which these cases tell apart.
 */
class LoadingXpReplayTest {

    private static final UUID ANN = new UUID(0, 1);
    // resources.yml weak-resources and attributes.yml kill-xp, as shipped
    private static final int GAP = 20;
    private static final double FACTOR = 0.25;
    private static final KillXp.Params KILL = KillXp.Params.DEFAULTS;

    /** A resource's XP at a skill level (the weak-resource rule), as GatheringService.evaluate builds it. */
    private static IntToDoubleFunction resource(double xp, int level) {
        return skillLevel -> GatherRules.xp(xp, level, skillLevel, GAP, FACTOR);
    }

    private static final IntToDoubleFunction COAL = resource(4, 1), POPPY = resource(3, 1), WHEAT = resource(3, 1);

    /** One player: the stored skills, whether the load has set them yet, the fraction carry, the level-ups shown. */
    private static final class Player {
        final Map<String, Integer> levels = new HashMap<>(), xp = new HashMap<>();
        final GatherRules.Carry carry = new GatherRules.Carry();
        final List<String> levelUps = new ArrayList<>();
        boolean loaded;

        Player(boolean loaded) { this.loaded = loaded; }

        Player at(String skill, int level, int xp) { levels.put(skill, level); this.xp.put(skill, xp); return this; }

        /** SkillManager.getSkillLevel: 1 until the load has set the skills. */
        int level(String skill) { return loaded ? levels.getOrDefault(skill, 1) : 1; }

        long total(String skill) { return SkillProgression.totalXp(levels.getOrDefault(skill, 1), xp.getOrDefault(skill, 0)); }

        /** SkillManager.applyAward. */
        void addXp(String skill, double amount) {
            int from = levels.getOrDefault(skill, 1);
            SkillProgression.Result r = SkillProgression.award(from, xp.getOrDefault(skill, 0), amount);
            if (r.levelsGained() > 0) levelUps.add(skill + " " + from + "->" + r.level());
            levels.put(skill, r.level());
            xp.put(skill, r.xp());
        }

        /** GatheringService.pay: fractions carried, whole points awarded. */
        void pay(String skill, double amount) {
            long whole = carry.add(skill, amount);
            if (whole > 0) addXp(skill, whole);
        }
    }

    /** How a session pays what it does while its skills load. */
    private enum Way {
        /** This build: the rule is kept and worked out when the load completes, at the level then. */
        EXACT,
        /** The previous build: the amount worked out when it happened (level 1 while loading), kept, added at the load. */
        PREVIOUS
    }

    /** A session: begins loading (unless the player is loaded already), acts, completes or fails its load. */
    private static final class Session {
        final LoadingXpBuffer buffer;
        final Player p;
        final Way way;
        final long generation;

        Session(LoadingXpBuffer buffer, Player p, Way way, long generation) {
            this.buffer = buffer;
            this.p = p;
            this.way = way;
            this.generation = generation;
            if (!p.loaded) buffer.begin(ANN, generation);
        }

        /** An action paying {@code xpAt} at a skill level: SkillManager.whenLoaded (EXACT) or the previous mapsOrKeep. */
        void act(String skill, IntToDoubleFunction xpAt) {
            if (way == Way.EXACT) {
                Runnable action = () -> p.pay(skill, xpAt.applyAsDouble(p.level(skill)));
                if (buffer.isLoading(ANN) && buffer.keep(ANN, action)) return;
                if (p.loaded) action.run(); // neither loading nor loaded (the load failed, the player left): dropped
            } else {
                double amount = xpAt.applyAsDouble(p.level(skill)); // read now: 1 while loading
                long whole = p.carry.add(skill, amount);
                if (whole <= 0) return;
                Runnable award = () -> p.addXp(skill, whole);
                if (p.loaded) award.run();
                else buffer.keep(ANN, award);
            }
        }

        /** A kill of a level-{@code mobLevel} mob (read when it died), paid as MobKillListener does. */
        void kill(String skill, int mobLevel) {
            IntToDoubleFunction xpAt = skillLevel -> KillXp.compute(mobLevel, 1.0, skillLevel, KILL);
            if (way == Way.EXACT) {
                Runnable action = () -> p.addXp(skill, xpAt.applyAsDouble(p.level(skill)));
                if (buffer.isLoading(ANN) && buffer.keep(ANN, action)) return;
                if (p.loaded) action.run();
            } else {
                double amount = xpAt.applyAsDouble(p.level(skill));
                Runnable award = () -> p.addXp(skill, amount);
                if (p.loaded) award.run();
                else buffer.keep(ANN, award);
            }
        }

        /** The load completed: the skills are set (setPlayerSkills), then what was kept runs, in order. */
        void loaded() {
            p.loaded = true;
            LoadingXpBuffer.Kept kept = buffer.drain(ANN, generation);
            if (kept != null) kept.actions().forEach(Runnable::run);
        }
    }

    private static Player veteran(boolean loaded) {
        return new Player(loaded).at("Mining", 60, 0).at("Herbalism", 60, 0).at("Farming", 60, 0);
    }

    private static void twelveBlocks(Session s) {
        for (int i = 0; i < 4; i++) {
            s.act("Mining", COAL);
            s.act("Herbalism", POPPY);
            s.act("Farming", WHEAT);
        }
    }

    /** What the same session start earns doing {@code what} while loading, by {@code way}, and once loaded. */
    private static Player whileLoading(Player start, Way way, java.util.function.Consumer<Session> what) {
        Session s = new Session(new LoadingXpBuffer(), start, way, 1);
        what.accept(s);
        s.loaded();
        return start;
    }

    private static Player alreadyLoaded(Player start, java.util.function.Consumer<Session> what) {
        what.accept(new Session(new LoadingXpBuffer(), start, Way.EXACT, 1));
        return start;
    }

    @Test
    void aNewLevelOnePlayerIsPaidWhatALoadedOneIs() {
        Player loading = whileLoading(new Player(false), Way.EXACT, LoadingXpReplayTest::twelveBlocks);
        Player loaded = alreadyLoaded(new Player(true), LoadingXpReplayTest::twelveBlocks);
        Player previous = whileLoading(new Player(false), Way.PREVIOUS, LoadingXpReplayTest::twelveBlocks);
        for (String skill : List.of("Mining", "Herbalism", "Farming")) {
            assertEquals(loaded.total(skill), loading.total(skill), skill);
            assertEquals(loaded.total(skill), previous.total(skill), skill + ": level 1 was always right (the regression guard)");
        }
        assertEquals(SkillProgression.totalXp(1, 0) + 16, loading.total("Mining"));
        assertEquals(SkillProgression.totalXp(1, 0) + 12, loading.total("Herbalism"));
    }

    @Test
    void severalActionsOfAnExperiencedPlayerArePaidAtTheirRealLevel() {
        Player loading = whileLoading(veteran(false), Way.EXACT, LoadingXpReplayTest::twelveBlocks);
        Player loaded = alreadyLoaded(veteran(true), LoadingXpReplayTest::twelveBlocks);
        Player previous = whileLoading(veteran(false), Way.PREVIOUS, LoadingXpReplayTest::twelveBlocks);
        long base = SkillProgression.totalXp(60, 0);
        // level-1 resources at level 60 pay a quarter (weak-resources): coal 1, poppy and wheat 0.75
        assertEquals(base + 4, loading.total("Mining"));
        assertEquals(base + 3, loading.total("Herbalism"));
        assertEquals(base + 3, loading.total("Farming"));
        for (String skill : List.of("Mining", "Herbalism", "Farming")) {
            assertEquals(loaded.total(skill), loading.total(skill), skill + ": what a loaded player gets");
            assertNotEquals(loaded.total(skill), previous.total(skill), skill + ": the previous buffer paid level 1's XP");
        }
        assertEquals(base + 16, previous.total("Mining"), "the previous buffer: 4x for the same coal");
    }

    @Test
    void earlierActionsLevelUpsChangeWhatTheLaterOnesPay() {
        // Mining 20, 2 XP short of 21: the first coal pays in full (20 - 1 < 20) and levels up; at 21 the next one is weak
        int need = SkillManager.xpForLevel(20);
        java.util.function.Consumer<Session> twoCoal = s -> { s.act("Mining", COAL); s.act("Mining", COAL); };
        Player loading = whileLoading(new Player(false).at("Mining", 20, need - 2), Way.EXACT, twoCoal);
        Player loaded = alreadyLoaded(new Player(true).at("Mining", 20, need - 2), twoCoal);
        Player previous = whileLoading(new Player(false).at("Mining", 20, need - 2), Way.PREVIOUS, twoCoal);
        assertEquals(21, loading.levels.get("Mining"));
        assertEquals(3, loading.xp.get("Mining"), "4 (2 to the level-up, 2 into 21), then a quarter of 4 at 21");
        assertEquals(List.of("Mining 20->21"), loading.levelUps, "the level-up shown once, as in play");
        assertEquals(loaded.levels, loading.levels);
        assertEquals(loaded.xp, loading.xp);
        assertEquals(6, previous.xp.get("Mining"), "the previous buffer paid both in full (Mining 21, 6 XP)");
    }

    @Test
    void aKillIsPaidAtTheRestoredLevelWithTheMobReadWhenItDied() {
        // a level-6 zombie (persist.js's arena, ~850 blocks out): 70 XP at Blade Mastery 1, a quarter at 60 (anti-grind)
        java.util.function.Consumer<Session> kill = s -> s.kill("Blade Mastery", 6);
        Player loading = whileLoading(new Player(false).at("Blade Mastery", 60, 0), Way.EXACT, kill);
        Player loaded = alreadyLoaded(new Player(true).at("Blade Mastery", 60, 0), kill);
        Player previous = whileLoading(new Player(false).at("Blade Mastery", 60, 0), Way.PREVIOUS, kill);
        long base = SkillProgression.totalXp(60, 0);
        assertEquals(base + 17, loading.total("Blade Mastery"), "17.5: 17 whole");
        assertEquals(loaded.total("Blade Mastery"), loading.total("Blade Mastery"));
        assertEquals(base + 70, previous.total("Blade Mastery"), "the previous buffer: worked out at level 1");
    }

    @Test
    void aFailedLoadPaysNothingAndARejoinPaysOnlyItsOwnActionsOnce() {
        LoadingXpBuffer buffer = new LoadingXpBuffer();
        Player p = veteran(false);
        long start = p.total("Mining");
        Session first = new Session(buffer, p, Way.EXACT, 1);
        first.act("Mining", COAL);
        first.act("Mining", COAL);
        buffer.abandon(ANN, 1); // the load failed: the player is disconnected
        first.act("Mining", COAL); // neither loading nor loaded: dropped
        assertNull(buffer.drain(ANN, 1));
        assertEquals(start, p.total("Mining"), "a failed load pays nothing");

        // a quick rejoin (session 2), and another before session 2's load came back (session 3)
        Session second = new Session(buffer, p, Way.EXACT, 2);
        second.act("Mining", COAL);
        Session third = new Session(buffer, p, Way.EXACT, 3);
        third.act("Mining", COAL);
        third.act("Mining", COAL);
        p.loaded = true;
        assertNull(buffer.drain(ANN, 2), "session 2's late load: nothing");
        assertEquals(start, p.total("Mining"));
        third.loaded();
        assertEquals(start + 2, p.total("Mining"), "session 3's two coal at a quarter each, nothing of sessions 1 and 2");
        third.act("Mining", COAL); // loaded now: paid at once
        assertEquals(start + 3, p.total("Mining"));
    }
}

package Attributes;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class AttributeServiceTest {

    /** A source whose sword Power we can change, counting how often it is asked. */
    private static final class FakeSource implements AttributeSource {
        final Map<UUID, Double> power = new ConcurrentHashMap<>();
        int calls;
        @Override public String id() { return "fake"; }
        @Override public List<Contribution> contributions(UUID p, AttributeRules r) {
            calls++;
            return List.of(Contribution.attribute("fake", "Fake", Attribute.POWER, power.getOrDefault(p, 0.0), WeaponClass.SWORD));
        }
    }

    private final AtomicReference<AttributeRules> rules = new AtomicReference<>(AttributeRules.DEFAULTS);
    private final AttributeService service = new AttributeService(rules::get);
    private final FakeSource source = new FakeSource();
    private final UUID alice = UUID.randomUUID();

    AttributeServiceTest() {
        service.addSource(source);
    }

    @Test
    void untrackedPlayersGetTheNeutralSnapshotAndNothingIsCached() {
        source.power.put(alice, 50.0);
        AttributeSnapshot s = service.get(alice);
        assertEquals(0, s.power(WeaponClass.SWORD));
        assertEquals(0, service.cachedCount());
        assertEquals(0, source.calls, "sources are not even asked");
    }

    @Test
    void snapshotIsCachedUntilMarkedDirty() {
        source.power.put(alice, 10.0);
        service.track(alice);
        assertEquals(10, service.get(alice).power(WeaponClass.SWORD));
        assertEquals(10, service.get(alice).power(WeaponClass.SWORD));
        assertEquals(1, source.calls, "second read is served from the cache");

        source.power.put(alice, 20.0);
        assertEquals(10, service.get(alice).power(WeaponClass.SWORD), "stale until told");
        service.markDirty(alice);
        assertEquals(20, service.get(alice).power(WeaponClass.SWORD));
        assertEquals(2, source.calls);
    }

    @Test
    void reloadingTheRulesRebuildsEverySnapshot() {
        service.track(alice);
        source.power.put(alice, 10.0);
        double before = service.get(alice).powerMultiplier(WeaponClass.SWORD);
        AttributeRules d = AttributeRules.DEFAULTS;
        rules.set(new AttributeRules(d.skillWeights(), d.caps(),
                new AttributeRules.Derived(0.10, 0.01, 0.001, 0.01, 0.002, 0.01, 0.01, 0.02),
                d.limits(), d.curves(), d.switches()));
        double after = service.get(alice).powerMultiplier(WeaponClass.SWORD);
        assertEquals(1.5, before, 1e-9);
        assertEquals(2.0, after, 1e-9);
    }

    @Test
    void untrackForgetsThePlayer() {
        service.track(alice);
        service.get(alice);
        assertEquals(1, service.cachedCount());
        service.untrack(alice);
        assertEquals(0, service.cachedCount());
        assertEquals(0, service.trackedCount());
        service.get(alice);
        assertEquals(0, service.cachedCount(), "a late read after quit does not bring the player back");
    }

    @Test
    void previewIsNeverCached() {
        service.track(alice);
        source.power.put(alice, 10.0);
        AttributeSnapshot what = service.preview(alice, cs -> {
            List<Contribution> out = new ArrayList<>(cs);
            out.add(Contribution.attribute("sim", "Simulated", Attribute.POWER, 40, WeaponClass.SWORD));
            return out;
        });
        assertEquals(50, what.power(WeaponClass.SWORD));
        assertEquals(10, service.get(alice).power(WeaponClass.SWORD));
    }

    @Test
    void aSkillLevelPreviewAsksLevelGatedSourcesAtThatLevel() {
        // Like the fishing setup's Lure parts, which open at Fishing 25: a level-up to 25 shows them as its gain.
        AttributeSource gated = new AttributeSource() {
            @Override public String id() { return "gated"; }
            @Override public List<Contribution> contributions(UUID p, AttributeRules r) { return at(30); }
            @Override public List<Contribution> contributionsAt(UUID p, AttributeRules r, String skill, int level) {
                return skill.equals("Fishing") ? at(level) : contributions(p, r);
            }
            private List<Contribution> at(int level) {
                return level >= 25 ? List.of(Contribution.attribute("gated", "Gated", Attribute.POWER, 7, WeaponClass.SWORD)) : List.of();
            }
        };
        service.addSource(gated);
        service.track(alice);
        assertEquals(7, service.get(alice).power(WeaponClass.SWORD), "the player is at 30 now");
        assertEquals(0, service.withSkillLevel(alice, "Fishing", 24).power(WeaponClass.SWORD), "at 24 the part isn't open");
        assertEquals(7, service.withSkillLevel(alice, "Fishing", 25).power(WeaponClass.SWORD));
        assertEquals(7, service.withSkillLevel(alice, "Mining", 1).power(WeaponClass.SWORD), "another skill's level changes nothing here");
    }
}

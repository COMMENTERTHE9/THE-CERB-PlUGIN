package Manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Ranged mob attacks scale like melee, within their own caps (owner decision 10, 2026-10-27). */
class MobDamageScalingTest {

    private static MobDamageScaling shipped() throws Exception {
        YamlConfiguration c = new YamlConfiguration();
        c.loadFromString(java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/combat.yml")));
        MobDamageScaling s = new MobDamageScaling();
        s.loadConfig(c);
        return s;
    }

    @Test
    void rangedFollowsMeleeUpToItsOwnCap() throws Exception {
        MobDamageScaling s = shipped();
        // A COMMON skeleton: the same multiplier as a COMMON zombie's melee hit at every level.
        for (int level : new int[] {1, 25, 50, 100}) {
            assertEquals(s.calculate(Tier.COMMON, level), s.calculateRanged(Tier.COMMON, level), 1e-12, "COMMON level " + level);
        }
        assertEquals(1.022, s.calculateRanged(Tier.COMMON, 1), 0.001, "level 1 is practically vanilla");
        assertEquals(1.885, s.calculateRanged(Tier.COMMON, 100), 0.001);
        // High tiers: melee reaches 8.48 at PRIMORDIAL 100; ranged stops at 6.
        assertEquals(8.48, s.calculate(Tier.PRIMORDIAL, 100), 0.01);
        assertEquals(6.0, s.calculateRanged(Tier.PRIMORDIAL, 100), 1e-12);
        assertEquals(1.35 * 1.885, s.calculateRanged(Tier.RARE, 100), 0.01, "a RARE level-100 mob, under the cap");
    }

    /** A full-power arrow (about 6) against a fresh player (100 health) and a trained one (300). */
    @Test
    void oneShotFromRangeIsImpossibleButHighLevelShotsHurt() throws Exception {
        MobDamageScaling s = shipped();
        double arrow = 6.0;
        double common100 = arrow * s.calculateRanged(Tier.COMMON, 100);                  // 11.3
        assertEquals(11.3, s.capRangedHit(arrow, common100, 100), 0.05, "under the cap: unchanged");
        double primordial100 = arrow * s.calculateRanged(Tier.PRIMORDIAL, 100);          // 36
        assertEquals(35.0, s.capRangedHit(arrow, primordial100, 100), 1e-9, "a fresh player loses at most 35% per shot");
        assertEquals(36.0, s.capRangedHit(arrow, primordial100, 300), 1e-9, "a trained player is under the cap");
        // Never weaker than vanilla, even when the vanilla hit alone is above the share (a tiny max health).
        assertEquals(20.0, s.capRangedHit(20.0, 40.0, 40), 1e-9);
    }

    @Test
    void anOlderCombatYmlGetsTheDefaultsAndBadValuesFallBack() throws Exception {
        YamlConfiguration old = new YamlConfiguration();
        old.loadFromString("mob-damage-scaling:\n  enabled: true\n  level-scale: 0.55\n  level-pivot: 25.0\n  max-damage-multiplier: 10.0\n");
        MobDamageScaling s = new MobDamageScaling();
        s.loadConfig(old);
        assertEquals(MobDamageScaling.DEFAULT_RANGED_MAX_MULTIPLIER, s.calculateRanged(Tier.PRIMORDIAL, 100), 1e-12);
        assertEquals(35.0, s.capRangedHit(6, 50, 100), 1e-9);

        YamlConfiguration off = new YamlConfiguration();
        off.loadFromString("mob-damage-scaling:\n  ranged:\n    enabled: false\n");
        s.loadConfig(off);
        assertEquals(1.0, s.calculateRanged(Tier.PRIMORDIAL, 100), 1e-12, "ranged scaling can be switched off");
        assertEquals(50.0, s.capRangedHit(6, 50, 100), 1e-12);

        YamlConfiguration bad = new YamlConfiguration();
        bad.loadFromString("mob-damage-scaling:\n  ranged:\n    max-damage-multiplier: 0.5\n    max-hit-share: 2\n");
        s.loadConfig(bad);
        assertEquals(MobDamageScaling.DEFAULT_RANGED_MAX_MULTIPLIER, s.calculateRanged(Tier.PRIMORDIAL, 100), 1e-12);
        assertEquals(35.0, s.capRangedHit(6, 50, 100), 1e-9);
    }
}

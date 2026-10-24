package Listener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DamageDisplayParticleTest {

    @Test
    void smallHitsKeepTwoParticlesPerDamagePoint() {
        assertEquals(1, DamageDisplayListener.particleCount(0.3));
        assertEquals(2, DamageDisplayListener.particleCount(1.3));
        assertEquals(20, DamageDisplayListener.particleCount(10));
    }

    @Test
    void hugeHitsAreCapped() {
        int max = DamageDisplayListener.MAX_HIT_PARTICLES;
        assertEquals(max, DamageDisplayListener.particleCount(1.0e9));
        assertEquals(max, DamageDisplayListener.particleCount(3.4e38)); // the /kill damage
        assertEquals(max, DamageDisplayListener.particleCount(Double.POSITIVE_INFINITY));
    }

    @Test
    void invalidDamageGivesOneParticle() {
        assertEquals(1, DamageDisplayListener.particleCount(Double.NaN));
        assertEquals(1, DamageDisplayListener.particleCount(-5));
        assertEquals(1, DamageDisplayListener.particleCount(0));
    }
}

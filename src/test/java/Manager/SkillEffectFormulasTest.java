package Manager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The Survival resist rules the combat code and the skill pages both read. */
class SkillEffectFormulasTest {

    @Test
    void survivalResistsGrowOnePercentPerLevelUpToTheirCaps() {
        assertEquals(0.0, PlayerDefenseManager.survivalFireResist(0), 1e-12);
        assertEquals(0.10, PlayerDefenseManager.survivalFireResist(10), 1e-12);
        assertEquals(0.30, PlayerDefenseManager.survivalFireResist(100), 1e-12);
        assertEquals(0.20, PlayerDefenseManager.survivalColdResist(100), 1e-12);
        assertEquals(0.15, PlayerDefenseManager.survivalDrowningResist(15), 1e-12);
        assertEquals(0.0, PlayerDefenseManager.survivalColdResist(-5), 1e-12);
    }
}

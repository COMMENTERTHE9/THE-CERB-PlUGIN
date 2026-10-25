package Manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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

    /** Fire resistance covers every kind of fire (decision C); lava gets half by default. */
    @Test
    void fireResistanceCoversEveryKindOfFire() throws Exception {
        YamlConfiguration cfg;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("combat.yml")) {
            cfg = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        Map<CustomDamageType, Double> m = CombatConfig.fireCoverageFrom(cfg.getConfigurationSection("fire-resistance"));
        for (CustomDamageType t : new CustomDamageType[]{CustomDamageType.FIRE, CustomDamageType.FIRE_TICK, CustomDamageType.PYROMANCY,
                CustomDamageType.BURN_DOT, CustomDamageType.CAMPFIRE, CustomDamageType.HOT_FLOOR}) {
            assertEquals(1.0, m.get(t), 1e-12, t.name());
        }
        assertEquals(0.5, m.get(CustomDamageType.LAVA), 1e-12);
        assertEquals(null, m.get(CustomDamageType.ENTITY_ATTACK));
        // An older combat.yml without the section gets the same defaults; broken values are clamped.
        assertEquals(m, CombatConfig.fireCoverageFrom(null));
        YamlConfiguration bad = new YamlConfiguration();
        bad.set("fire-resistance.lava", 7.0);
        bad.set("fire-resistance.burning", -1.0);
        Map<CustomDamageType, Double> c = CombatConfig.fireCoverageFrom(bad.getConfigurationSection("fire-resistance"));
        assertEquals(1.0, c.get(CustomDamageType.LAVA), 1e-12);
        assertEquals(0.0, c.get(CustomDamageType.FIRE_TICK), 1e-12);
    }
}

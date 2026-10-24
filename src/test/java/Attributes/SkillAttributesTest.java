package Attributes;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SkillAttributesTest {

    private static final AttributeRules R = AttributeRules.DEFAULTS;

    private static AttributeSnapshot snap(Map<String, Integer> levels) {
        return AttributeCalculator.compute(SkillAttributes.contributions(levels, R), R);
    }

    @Test
    void bladeMasteryIsSwordPower() {
        AttributeSnapshot s = snap(Map.of("Blade Mastery", 100));
        assertEquals(100, s.power(WeaponClass.SWORD));
        assertEquals(0, s.power(WeaponClass.AXE));
        assertEquals(6.0, s.powerMultiplier(WeaponClass.SWORD), 1e-9);
    }

    @Test
    void weaponMasteryIsAxeMaceTridentSpearPlusAQuarterEverywhere() {
        AttributeSnapshot s = snap(Map.of("Weapon Mastery", 40, "Blade Mastery", 10));
        assertEquals(50, s.power(WeaponClass.AXE));
        assertEquals(50, s.power(WeaponClass.MACE));
        assertEquals(50, s.power(WeaponClass.TRIDENT));
        assertEquals(50, s.power(WeaponClass.SPEAR));
        assertEquals(20, s.power(WeaponClass.SWORD), "10 Blade Mastery + 10 from Weapon Mastery's quarter");
        assertEquals(10, s.power(WeaponClass.BOW));
        assertTrue(s.lifesteal() > 0, "Weapon Mastery also gives lifesteal");
    }

    @Test
    void defensiveAndMagicSkillsFeedTheirAttributes() {
        AttributeSnapshot s = snap(Map.of("Survival", 100, "Heavy Armor Training", 100, "First Aid", 100,
                "Intelligence", 50, "Mana Regeneration", 50, "Critical Strike", 30, "Scavenging", 10));
        assertEquals(200, s.get(Attribute.VITALITY));       // 100 + 50 + 50
        assertEquals(150, s.get(Attribute.FORTITUDE));      // 100 + 50
        assertEquals(100, s.get(Attribute.ESSENCE));
        assertEquals(30, s.get(Attribute.AGILITY));
        assertEquals(5, s.get(Attribute.FORTUNE));
        assertEquals(3.0, s.maxHpMultiplier(), 1e-9);
        assertEquals(2.0, s.maxManaMultiplier(), 1e-9);
    }

    @Test
    void unlistedAndZeroLevelSkillsAddNothing() {
        assertTrue(SkillAttributes.contributions(Map.of("Mining", 99, "Blade Mastery", 0), R).isEmpty());
    }

    @Test
    void labelsNameTheSkillAndLevel() {
        List<Contribution> cs = SkillAttributes.contributions(Map.of("Blade Mastery", 42), R);
        assertEquals("Blade Mastery 42", cs.get(0).label());
        assertEquals("skill:Blade Mastery", cs.get(0).sourceId());
    }

    @Test
    void weaponClassFromMaterial() {
        assertEquals(WeaponClass.SWORD, WeaponClass.ofMaterial("NETHERITE_SWORD"));
        assertEquals(WeaponClass.AXE, WeaponClass.ofMaterial("IRON_AXE"));
        assertEquals(WeaponClass.OTHER, WeaponClass.ofMaterial("DIAMOND_PICKAXE"));
        assertEquals(WeaponClass.SPEAR, WeaponClass.ofMaterial("IRON_SPEAR"));
        assertEquals(WeaponClass.MACE, WeaponClass.ofMaterial("MACE"));
        assertEquals(WeaponClass.BOW, WeaponClass.ofMaterial("CROSSBOW"));
        assertEquals(WeaponClass.UNARMED, WeaponClass.ofMaterial("AIR"));
        assertEquals(WeaponClass.OTHER, WeaponClass.ofMaterial("DIRT"));
    }

    /** The shipped attributes.yml must say exactly what the code defaults say. */
    @Test
    void bundledConfigMatchesDefaults() throws Exception {
        YamlConfiguration cfg;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("attributes.yml")) {
            assertNotNull(in, "attributes.yml is on the classpath");
            cfg = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        List<String> problems = new ArrayList<>();
        AttributeRules parsed = AttributesConfig.parseRules(cfg.getConfigurationSection("attributes"),
                AttributeRules.DEFAULT_CURVES, problems::add);
        assertEquals(List.of(), problems);
        assertEquals(R.skillWeights(), parsed.skillWeights());
        assertEquals(R.caps(), parsed.caps());
        assertEquals(R.derived(), parsed.derived());
        assertEquals(R.limits(), parsed.limits());
        assertEquals(R.switches(), parsed.switches());
    }

    /** A server's attributes.yml from before the attributes section existed gets the shipped weights. */
    @Test
    void olderConfigFileFallsBackToTheShippedSection() throws Exception {
        YamlConfiguration bundled;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("attributes.yml")) {
            bundled = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        YamlConfiguration old = new YamlConfiguration();
        old.set("kill-xp.enabled", true);
        old.setDefaults(bundled);
        List<String> problems = new ArrayList<>();
        AttributeRules parsed = AttributesConfig.parseRules(AttributesConfig.section(old, "attributes"),
                AttributeRules.DEFAULT_CURVES, problems::add);
        assertEquals(List.of(), problems);
        assertEquals(R.skillWeights(), parsed.skillWeights());

        // Same when the file has the section but not every part of it.
        YamlConfiguration partial = new YamlConfiguration();
        partial.set("attributes.caps.power", 250.0);
        partial.setDefaults(bundled);
        AttributeRules p2 = AttributesConfig.parseRules(AttributesConfig.section(partial, "attributes"),
                AttributeRules.DEFAULT_CURVES, problems::add);
        assertEquals(R.skillWeights(), p2.skillWeights());
        assertEquals(250.0, p2.cap(Attribute.POWER));
    }

    @Test
    void weaponAllMeansEveryWeapon() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("attributes.skills.Weapon Mastery", List.of(Map.of("attribute", "power", "per-level", 0.25, "weapon", "all")));
        List<String> problems = new ArrayList<>();
        AttributeRules r = AttributesConfig.parseRules(cfg.getConfigurationSection("attributes"), AttributeRules.DEFAULT_CURVES, problems::add);
        assertEquals(List.of(), problems);
        assertEquals(List.of(new AttributeRules.SkillWeight(Attribute.POWER, 0.25, null)), r.skillWeights().get("Weapon Mastery"));
    }

    /** Config keys must not depend on the server's locale (Turkish dotless i). */
    @Test
    void capsReadTheSameUnderATurkishLocale() {
        java.util.Locale before = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            YamlConfiguration cfg = new YamlConfiguration();
            cfg.set("attributes.caps.vitality", 123.0);
            AttributeRules r = AttributesConfig.parseRules(cfg.getConfigurationSection("attributes"), AttributeRules.DEFAULT_CURVES, p -> {});
            assertEquals(123.0, r.cap(Attribute.VITALITY));
        } finally {
            java.util.Locale.setDefault(before);
        }
    }

    @Test
    void badConfigEntriesAreReportedAndSkipped() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("attributes.skills.Blade Mastery", List.of(Map.of("attribute", "powr", "per-level", 1.0)));
        cfg.set("attributes.skills.Survival", List.of(Map.of("attribute", "vitality", "per-level", 1.0, "weapon", "laser")));
        cfg.set("attributes.limits.crit-chance", 1.5);
        cfg.set("attributes.derived.power-damage", -1);
        List<String> problems = new ArrayList<>();
        AttributeRules r = AttributesConfig.parseRules(cfg.getConfigurationSection("attributes"), AttributeRules.DEFAULT_CURVES, problems::add);
        assertEquals(4, problems.size(), String.join("\n", problems));
        assertTrue(r.skillWeights().isEmpty());
        assertEquals(R.limits().critChance(), r.limits().critChance());
        assertEquals(R.derived().powerDamage(), r.derived().powerDamage());
    }
}

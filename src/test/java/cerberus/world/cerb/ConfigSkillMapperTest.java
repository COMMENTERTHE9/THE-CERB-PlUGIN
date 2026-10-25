package cerberus.world.cerb;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigSkillMapperTest {

    @Test
    void killItemsReadAsWords() {
        assertEquals("any sword", ConfigSkillMapper.describeItems(List.of(), List.of(".*_SWORD")));
        assertEquals("mace, trident, any axe", ConfigSkillMapper.describeItems(List.of("MACE", "TRIDENT"), List.of(".*_AXE")));
        assertEquals("an empty hand", ConfigSkillMapper.describeItems(List.of("AIR"), List.of()));
        assertEquals("blaze rod, any wand, any staff",
                ConfigSkillMapper.describeItems(List.of("BLAZE_ROD"), List.of(".*WAND.*", ".*STAFF.*")));
        assertEquals("items matching (GOLD|IRON)_HOE", ConfigSkillMapper.describeItems(List.of(), List.of("(GOLD|IRON)_HOE")));
    }

    /** Spears train Weapon Mastery (decision E), also with a skills.yml written before spears existed. */
    @Test
    void spearsTrainWeaponMasteryEvenWithAnOlderSkillsYml() {
        List<ConfigSkillMapper.SkillTrigger> old = new ArrayList<>(List.of(
                new ConfigSkillMapper.SkillTrigger("Blade Mastery", 10, Set.of(), List.of(Pattern.compile(".*_SWORD"))),
                new ConfigSkillMapper.SkillTrigger("Weapon Mastery", 20, Set.of("MACE", "TRIDENT"), List.of(Pattern.compile(".*_AXE")))));
        ConfigSkillMapper.addSpearRuleIfMissing(old);
        assertTrue(old.get(1).matches("IRON_SPEAR"));
        assertTrue(old.get(1).matches("NETHERITE_AXE") && old.get(1).matches("MACE"));
        assertTrue(!old.get(0).matches("IRON_SPEAR"));

        // A rule of the owner's own for spears wins: nothing is added.
        List<ConfigSkillMapper.SkillTrigger> own = new ArrayList<>(List.of(
                new ConfigSkillMapper.SkillTrigger("Martial Expertise", 5, Set.of(), List.of(Pattern.compile(".*_SPEAR"))),
                new ConfigSkillMapper.SkillTrigger("Weapon Mastery", 20, Set.of("MACE"), List.of(Pattern.compile(".*_AXE")))));
        ConfigSkillMapper.addSpearRuleIfMissing(own);
        assertTrue(!own.get(1).matches("IRON_SPEAR"));
    }

    @Test
    void shippedSkillsYmlRoutesSpearsToWeaponMastery() throws Exception {
        YamlConfiguration cfg;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("skills.yml")) {
            cfg = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        List<String> rx = cfg.getStringList("skill-triggers.Weapon Mastery.match.material-regex");
        assertTrue(rx.contains(".*_SPEAR"), "skills.yml Weapon Mastery regex: " + rx);
    }
}

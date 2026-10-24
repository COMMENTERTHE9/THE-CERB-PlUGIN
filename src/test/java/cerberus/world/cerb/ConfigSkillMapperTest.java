package cerberus.world.cerb;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}

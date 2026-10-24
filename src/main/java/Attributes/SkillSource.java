package Attributes;

import Skills.SkillManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Attribute points from skill levels (weights in attributes.yml). Reads SkillManager's concurrent maps. */
public final class SkillSource implements AttributeSource {

    private final SkillManager skills;

    public SkillSource(SkillManager skills) {
        this.skills = skills;
    }

    @Override public String id() { return "skill"; }

    @Override
    public List<Contribution> contributions(UUID player, AttributeRules rules) {
        return SkillAttributes.contributions(levels(player, rules), rules);
    }

    /** The player's level in every skill the rules care about (unlearned skills count as level 1, as everywhere else). */
    public Map<String, Integer> levels(UUID player, AttributeRules rules) {
        Map<String, Integer> levels = new HashMap<>();
        for (String skill : rules.skillWeights().keySet()) levels.put(skill, skills.getSkillLevel(player, skill));
        String ls = rules.curves().lifestealSkill();
        if (ls != null) levels.put(ls, skills.getSkillLevel(player, ls));
        return levels;
    }
}

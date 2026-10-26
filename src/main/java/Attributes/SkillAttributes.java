package Attributes;

import Manager.ScalingFormula;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Pure: a player's skill levels → their attribute contributions, using {@link AttributeRules#skillWeights()}. */
public final class SkillAttributes {

    public static final String SOURCE_PREFIX = "skill:";

    private SkillAttributes() {}

    public static List<Contribution> contributions(Map<String, Integer> levels, AttributeRules rules) {
        List<Contribution> out = new ArrayList<>();
        for (Map.Entry<String, List<AttributeRules.SkillWeight>> e : rules.skillWeights().entrySet()) {
            String skill = e.getKey();
            int level = Math.max(0, levels.getOrDefault(skill, 0));
            if (level == 0) continue;
            for (AttributeRules.SkillWeight w : e.getValue()) {
                double amount = w.perLevel() * level;
                if (amount == 0) continue;
                out.add(new Contribution(SOURCE_PREFIX + skill, Skills.SkillNames.display(skill) + " " + level, w.attribute(), null, amount,
                        w.weapon(), w.attribute() == Attribute.FORTUNE ? w.yield() : null));
            }
        }
        for (Map.Entry<String, List<AttributeRules.StatWeight>> e : rules.secondary().skillWeights().entrySet()) {
            String skill = e.getKey();
            int level = Math.max(0, levels.getOrDefault(skill, 0));
            if (level == 0) continue;
            for (AttributeRules.StatWeight w : e.getValue()) {
                double amount = w.perLevel() * level;
                if (amount == 0) continue;
                out.add(Contribution.stat(SOURCE_PREFIX + skill, Skills.SkillNames.display(skill) + " " + level, w.stat(), amount, w.weapon()));
            }
        }
        AttributeRules.Curves cv = rules.curves();
        if (cv.lifestealSkill() != null) {
            int level = Math.max(0, levels.getOrDefault(cv.lifestealSkill(), 0));
            if (level > 0) {
                out.add(Contribution.stat(SOURCE_PREFIX + cv.lifestealSkill(), Skills.SkillNames.display(cv.lifestealSkill()) + " " + level,
                        Stat.LIFESTEAL, ScalingFormula.compute(level, cv.lifesteal()), null));
            }
        }
        return out;
    }
}

package com.scholardream.adaptiveboss.skill;

import java.util.List;
import java.util.Random;

/**
 * Week 2 baseline: picks uniformly at random, sometimes just keeps chasing.
 * This is the policy the adaptive one has to beat in the week-5 evaluation.
 */
public class RandomPolicy implements DecisionPolicy {
    private final Random random = new Random();

    @Override
    public String chooseSkill(SkillContext context, List<Skill> availableSkills) {
        if (availableSkills.isEmpty() || random.nextFloat() < 0.25f) {
            return null;
        }
        return availableSkills.get(random.nextInt(availableSkills.size())).id();
    }
}

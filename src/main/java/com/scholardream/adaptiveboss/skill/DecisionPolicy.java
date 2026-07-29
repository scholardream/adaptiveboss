package com.scholardream.adaptiveboss.skill;

import java.util.List;

/**
 * Pluggable "which skill now" brain. Implementations:
 *
 * <ul>
 *   <li>{@link RandomPolicy} — week 2 baseline</li>
 *   <li>SocketPolicy — week 3, asks the Python decision service over TCP</li>
 *   <li>BehaviorTreePolicy — fallback when Python is unreachable (never stand still)</li>
 * </ul>
 */
public interface DecisionPolicy {
    /**
     * @param context         current fight snapshot
     * @param availableSkills skills that are off cooldown AND canCast right now
     * @return id of the skill to wind up, or {@code null} to keep basic melee/chase
     */
    String chooseSkill(SkillContext context, List<Skill> availableSkills);
}

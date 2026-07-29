package com.scholardream.adaptiveboss.skill;

import com.scholardream.adaptiveboss.entity.AdaptiveBossEntity;
import com.scholardream.adaptiveboss.skill.skills.AreaSlam;
import com.scholardream.adaptiveboss.skill.skills.ChargeAttack;
import com.scholardream.adaptiveboss.skill.skills.ProjectileVolley;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the boss's skill state machine every tick:
 *
 * <pre>
 *   idle --(decision every 5 ticks, via DecisionPolicy)--> winding up --(windup ticks)--> cast --> cooldown
 * </pre>
 *
 * The decision source is pluggable; week 3 swaps in the Python bridge, and if
 * that dies mid-fight we degrade to a local policy instead of freezing.
 */
public class SkillScheduler {
    /** How often (ticks) the policy is asked for a decision. 5 ticks = 0.25 s. */
    public static final int DECISION_INTERVAL = 5;

    private final AdaptiveBossEntity boss;
    private final List<Skill> skills = new ArrayList<>();
    private final Map<String, Integer> cooldowns = new HashMap<>();

    private DecisionPolicy policy;

    private Skill windingUp = null;
    private int windupRemaining = 0;

    public SkillScheduler(AdaptiveBossEntity boss) {
        this.boss = boss;
        this.policy = new RandomPolicy();

        skills.add(new ChargeAttack());
        skills.add(new AreaSlam());
        skills.add(new ProjectileVolley());
    }

    public void setPolicy(DecisionPolicy policy) {
        this.policy = policy;
    }

    public List<Skill> getSkills() {
        return skills;
    }

    public void tick() {
        if (!(boss.getWorld() instanceof ServerWorld world) || !boss.isAlive()) {
            return;
        }
        LivingEntity target = boss.getTarget();
        SkillContext ctx = new SkillContext(boss, target, world);

        // tick cooldowns down
        cooldowns.replaceAll((id, ticks) -> Math.max(0, ticks - 1));

        // windup in progress: telegraph, then cast
        if (windingUp != null) {
            windingUp.onWindupTick(ctx, windupRemaining);
            if (--windupRemaining <= 0) {
                if (windingUp.canCast(ctx)) {
                    windingUp.cast(ctx);
                }
                cooldowns.put(windingUp.id(), windingUp.cooldownTicks());
                windingUp = null;
            }
        }

        // post-cast active phases (e.g. charge lunge)
        for (Skill skill : skills) {
            if (skill.isActive()) {
                skill.tickActive(ctx);
            }
        }

        // ask the policy for a decision every DECISION_INTERVAL ticks
        if (windingUp == null && ctx.hasTarget() && boss.age % DECISION_INTERVAL == 0) {
            List<Skill> available = skills.stream()
                    .filter(s -> cooldowns.getOrDefault(s.id(), 0) == 0)
                    .filter(s -> s.canCast(ctx))
                    .toList();
            if (available.isEmpty()) {
                return;
            }
            String chosenId = policy.chooseSkill(ctx, available);
            if (chosenId == null) {
                return;
            }
            for (Skill skill : available) {
                if (skill.id().equals(chosenId)) {
                    windingUp = skill;
                    windupRemaining = skill.windupTicks();
                    break;
                }
            }
        }
    }
}

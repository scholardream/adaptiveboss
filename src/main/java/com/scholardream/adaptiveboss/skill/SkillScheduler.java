package com.scholardream.adaptiveboss.skill;

import com.scholardream.adaptiveboss.bridge.BattleStateJson;
import com.scholardream.adaptiveboss.bridge.SocketPolicy;
import com.scholardream.adaptiveboss.config.ModConfig;
import com.scholardream.adaptiveboss.entity.AdaptiveBossEntity;
import com.scholardream.adaptiveboss.skill.skills.AreaSlam;
import com.scholardream.adaptiveboss.skill.skills.ChargeAttack;
import com.scholardream.adaptiveboss.skill.skills.ProjectileVolley;
import com.scholardream.adaptiveboss.skill.skills.PurgeWave;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
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
    private final AdaptiveBossEntity boss;
    private final List<Skill> skills = new ArrayList<>();
    private final Map<String, Integer> cooldowns = new HashMap<>();

    private DecisionPolicy policy;

    private Skill windingUp = null;
    private int windupRemaining = 0;

    public SkillScheduler(AdaptiveBossEntity boss) {
        this.boss = boss;
        // week 3: Python bridge policy, degrades to RandomPolicy internally
        this.policy = new SocketPolicy(this, boss.getBehaviorTracker());

        skills.add(new ChargeAttack());
        skills.add(new AreaSlam());
        skills.add(new ProjectileVolley());
        skills.add(new PurgeWave());
    }

    public void setPolicy(DecisionPolicy policy) {
        this.policy = policy;
    }

    public List<Skill> getSkills() {
        return skills;
    }

    public AdaptiveBossEntity getBoss() {
        return boss;
    }

    /** Remaining cooldown ticks for a skill id — exposed for the bridge state JSON. */
    public int getCooldownRemaining(String skillId) {
        return cooldowns.getOrDefault(skillId, 0);
    }

    /** Decision cadence, from config ({@code bridge.decisionIntervalTicks}). */
    private int decisionInterval() {
        return Math.max(1, ModConfig.get().bridge.decisionIntervalTicks);
    }

    /** Release background resources (bridge thread) when the boss is discarded. */
    public void shutdown() {
        if (policy instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                com.scholardream.adaptiveboss.AdaptiveBossMod.LOGGER.warn("[AdaptiveBoss] failed to shut down policy", e);
            }
        }
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
                    if (boss.getFightLogger() != null) {
                        boss.getFightLogger().recordSkillUse(windingUp.id());
                    }
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

        // ask the policy for a decision every decisionInterval() ticks
        if (windingUp == null && ctx.hasTarget() && boss.age % decisionInterval() == 0) {
            List<Skill> available = skills.stream()
                    .filter(s -> cooldowns.getOrDefault(s.id(), 0) == 0)
                    .filter(s -> s.canCast(ctx))
                    .toList();

            String chosenId = null;
            String source = "none"; // no skill available this frame
            if (!available.isEmpty()) {
                chosenId = policy.chooseSkill(ctx, available);
                source = policy instanceof SocketPolicy socketPolicy
                        ? socketPolicy.getLastDecisionSource()
                        : policy.getClass().getSimpleName();
            }

            // week 4: log one frame per decision, same cadence as the bridge
            if (boss.getFightLogger() != null && ctx.target() instanceof PlayerEntity) {
                boss.getFightLogger().logFrame(
                        BattleStateJson.toJsonObject(ctx, available, boss.getBehaviorTracker(), this),
                        chosenId, source);
            }

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

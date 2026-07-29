package com.scholardream.adaptiveboss.skill;

import com.scholardream.adaptiveboss.entity.AdaptiveBossEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Everything a skill needs to know about the current fight, captured at one
 * tick. The same context object is what the (future) Python decision bridge
 * will serialize — keep it free of anything non-serializable in spirit.
 */
public record SkillContext(AdaptiveBossEntity boss, LivingEntity target, ServerWorld world) {

    public double distanceToTarget() {
        return target == null ? Double.MAX_VALUE : boss.distanceTo(target);
    }

    public boolean hasTarget() {
        return target != null && target.isAlive();
    }
}

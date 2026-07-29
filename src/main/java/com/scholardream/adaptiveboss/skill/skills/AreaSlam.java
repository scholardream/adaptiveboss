package com.scholardream.adaptiveboss.skill.skills;

import com.scholardream.adaptiveboss.config.ModConfig;
import com.scholardream.adaptiveboss.skill.Skill;
import com.scholardream.adaptiveboss.skill.SkillContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * 范围震地 —— 克绕背贴脸。
 *
 * <p>Long, very visible windup (orange ring at the exact blast radius), then
 * AoE damage + heavy knockback around the boss. Standing inside the ring at
 * cast time hurts; walking out during the windup is free.
 */
public class AreaSlam extends Skill {
    private static final DustParticleEffect TELEGRAPH =
            new DustParticleEffect(new Vector3f(1.0f, 0.55f, 0.1f), 1.6f);

    @Override
    public String id() {
        return "area_slam";
    }

    @Override
    public int cooldownTicks() {
        return ModConfig.get().areaSlam.cooldownTicks;
    }

    @Override
    public int windupTicks() {
        return ModConfig.get().areaSlam.windupTicks;
    }

    @Override
    public boolean canCast(SkillContext ctx) {
        return ctx.hasTarget() && ctx.distanceToTarget() <= ModConfig.get().areaSlam.radius * 1.2;
    }

    @Override
    public void onWindupTick(SkillContext ctx, int ticksRemaining) {
        if (ticksRemaining % 4 == 0) {
            telegraphRing(ctx.world(), ctx.boss().getPos(), ModConfig.get().areaSlam.radius, TELEGRAPH);
        }
    }

    @Override
    public void cast(SkillContext ctx) {
        ModConfig.Slam cfg = ModConfig.get().areaSlam;

        for (PlayerEntity player : ctx.world().getEntitiesByClass(PlayerEntity.class,
                ctx.boss().getBoundingBox().expand(cfg.radius, 2.0, cfg.radius),
                p -> p.isAlive() && !p.isSpectator())) {
            player.damage(ctx.world().getDamageSources().mobAttack(ctx.boss()), cfg.damage);
            Vec3d away = player.getPos().subtract(ctx.boss().getPos()).normalize();
            player.addVelocity(away.multiply(cfg.knockback).add(0, 0.45, 0));
            player.velocityModified = true;
        }

        ctx.world().spawnParticles(ParticleTypes.EXPLOSION_EMITTER,
                ctx.boss().getX(), ctx.boss().getY() + 0.5, ctx.boss().getZ(), 1, 0, 0, 0, 0);
        telegraphRing(ctx.world(), ctx.boss().getPos(), cfg.radius, ParticleTypes.POOF);
        ctx.world().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 0.7f, 0.6f);
    }
}

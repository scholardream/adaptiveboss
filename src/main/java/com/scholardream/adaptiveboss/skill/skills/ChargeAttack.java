package com.scholardream.adaptiveboss.skill.skills;

import com.scholardream.adaptiveboss.config.ModConfig;
import com.scholardream.adaptiveboss.skill.Skill;
import com.scholardream.adaptiveboss.skill.SkillContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * 冲锋 —— 克远程风筝。
 *
 * <p>Locks onto the target during windup (red line telegraph on the ground),
 * then lunges. If the player sidesteps during the windup, the boss charges
 * past — that's the intended counter-play.
 */
public class ChargeAttack extends Skill {
    private static final DustParticleEffect TELEGRAPH =
            new DustParticleEffect(new Vector3f(1.0f, 0.15f, 0.15f), 1.4f);

    @Override
    public String id() {
        return "charge";
    }

    @Override
    public int cooldownTicks() {
        return ModConfig.get().charge.cooldownTicks;
    }

    @Override
    public int windupTicks() {
        return ModConfig.get().charge.windupTicks;
    }

    @Override
    public boolean canCast(SkillContext ctx) {
        ModConfig.Charge cfg = ModConfig.get().charge;
        double distance = ctx.distanceToTarget();
        return ctx.hasTarget() && distance >= cfg.minRange && distance <= cfg.maxRange;
    }

    @Override
    public void onWindupTick(SkillContext ctx, int ticksRemaining) {
        if (ctx.hasTarget() && ticksRemaining % 3 == 0) {
            telegraphLine(ctx.world(), ctx.boss().getPos(), ctx.target().getPos(), TELEGRAPH);
        }
    }

    @Override
    public void cast(SkillContext ctx) {
        ModConfig.Charge cfg = ModConfig.get().charge;
        Vec3d direction = ctx.target().getPos().subtract(ctx.boss().getPos());
        direction = new Vec3d(direction.x, 0, direction.z).normalize();

        ctx.boss().setVelocity(direction.multiply(cfg.speed).add(0, 0.25, 0));
        ctx.boss().velocityModified = true;
        ctx.world().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.HOSTILE, 1.2f, 0.8f);

        setActiveTicks(cfg.lungeTicks);
    }

    @Override
    public void tickActive(SkillContext ctx) {
        ModConfig.Charge cfg = ModConfig.get().charge;
        for (PlayerEntity player : ctx.world().getEntitiesByClass(PlayerEntity.class,
                ctx.boss().getBoundingBox().expand(cfg.hitRadius),
                p -> p.isAlive() && !p.isSpectator())) {
            player.damage(ctx.world().getDamageSources().mobAttack(ctx.boss()), cfg.damage);
            Vec3d away = player.getPos().subtract(ctx.boss().getPos()).normalize();
            player.addVelocity(away.multiply(1.2).add(0, 0.4, 0));
            player.velocityModified = true;
            setActiveTicks(0); // hit once per charge
            return;
        }
        // stop the lunge when we slam into a wall
        if (ctx.boss().horizontalCollision) {
            setActiveTicks(0);
            ctx.boss().setVelocity(Vec3d.ZERO);
            return;
        }
        super.tickActive(ctx);
    }
}

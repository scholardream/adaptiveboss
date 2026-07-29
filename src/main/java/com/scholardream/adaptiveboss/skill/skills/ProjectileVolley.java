package com.scholardream.adaptiveboss.skill.skills;

import com.scholardream.adaptiveboss.config.ModConfig;
import com.scholardream.adaptiveboss.skill.Skill;
import com.scholardream.adaptiveboss.skill.SkillContext;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * 弹幕 —— 克站桩输出。
 *
 * <p>Short windup (purple ring tight around the boss), then a fan of
 * fireballs toward where the target IS at cast time. Strafing during the
 * windup dodges the whole volley.
 */
public class ProjectileVolley extends Skill {
    private static final DustParticleEffect TELEGRAPH =
            new DustParticleEffect(new Vector3f(0.7f, 0.2f, 1.0f), 1.4f);

    @Override
    public String id() {
        return "projectile_volley";
    }

    @Override
    public int cooldownTicks() {
        return ModConfig.get().projectileVolley.cooldownTicks;
    }

    @Override
    public int windupTicks() {
        return ModConfig.get().projectileVolley.windupTicks;
    }

    @Override
    public boolean canCast(SkillContext ctx) {
        double distance = ctx.distanceToTarget();
        return ctx.hasTarget() && distance >= 6.0 && distance <= ModConfig.get().projectileVolley.maxRange;
    }

    @Override
    public void onWindupTick(SkillContext ctx, int ticksRemaining) {
        if (ticksRemaining % 2 == 0) {
            telegraphRing(ctx.world(), ctx.boss().getPos(), 1.5, TELEGRAPH);
        }
    }

    @Override
    public void cast(SkillContext ctx) {
        ModConfig.Volley cfg = ModConfig.get().projectileVolley;
        Vec3d toTarget = ctx.target().getEyePos().subtract(ctx.boss().getEyePos()).normalize();

        for (int i = 0; i < cfg.projectileCount; i++) {
            double offset = cfg.projectileCount == 1
                    ? 0
                    : (i - (cfg.projectileCount - 1) / 2.0) * (cfg.spreadDegrees / (cfg.projectileCount - 1));
            Vec3d direction = toTarget.rotateY((float) Math.toRadians(offset));

            SmallFireballEntity fireball = new SmallFireballEntity(
                    ctx.world(), ctx.boss(), direction.multiply(cfg.projectileSpeed));
            fireball.setPosition(ctx.boss().getX(), ctx.boss().getEyeY() - 0.2, ctx.boss().getZ());
            ctx.world().spawnEntity(fireball);
        }

        ctx.world().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.HOSTILE, 1.0f, 0.9f);
    }
}

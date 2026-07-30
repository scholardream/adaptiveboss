package com.scholardream.adaptiveboss.skill.skills;

import com.scholardream.adaptiveboss.config.ModConfig;
import com.scholardream.adaptiveboss.skill.Skill;
import com.scholardream.adaptiveboss.skill.SkillContext;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.joml.Vector3f;

import java.util.List;

/**
 * 净化波 —— 克叠满 buff 的站桩玩家（"灌牛奶"）。
 *
 * <p>Only castable while the target carries a beneficial effect at or above
 * {@code purgeWave.minBuffLevel}. On cast, every player inside the radius
 * loses all beneficial effects of that level or higher — weaker buffs are
 * left alone, so this punishes overtuned setups rather than normal potion
 * use.
 */
public class PurgeWave extends Skill {
    private static final DustParticleEffect TELEGRAPH =
            new DustParticleEffect(new Vector3f(0.5f, 0.9f, 1.0f), 1.4f);

    @Override
    public String id() {
        return "purge_wave";
    }

    @Override
    public int cooldownTicks() {
        return ModConfig.get().purgeWave.cooldownTicks;
    }

    @Override
    public int windupTicks() {
        return ModConfig.get().purgeWave.windupTicks;
    }

    @Override
    public boolean canCast(SkillContext ctx) {
        if (!(ctx.target() instanceof PlayerEntity player) || !player.isAlive()) {
            return false;
        }
        return hasOvertunedBuff(player);
    }

    @Override
    public void onWindupTick(SkillContext ctx, int ticksRemaining) {
        if (ticksRemaining % 4 == 0) {
            telegraphRing(ctx.world(), ctx.boss().getPos(), ModConfig.get().purgeWave.radius, TELEGRAPH);
        }
    }

    @Override
    public void cast(SkillContext ctx) {
        ModConfig.Purge cfg = ModConfig.get().purgeWave;

        for (PlayerEntity player : ctx.world().getEntitiesByClass(PlayerEntity.class,
                ctx.boss().getBoundingBox().expand(cfg.radius, 4.0, cfg.radius),
                p -> p.isAlive() && !p.isSpectator())) {
            List<StatusEffectInstance> overtuned = player.getStatusEffects().stream()
                    .filter(this::isOvertunedBuff)
                    .toList();
            for (StatusEffectInstance effect : overtuned) {
                player.removeStatusEffect(effect.getEffectType());
            }
            if (!overtuned.isEmpty()) {
                ctx.world().spawnParticles(ParticleTypes.REVERSE_PORTAL,
                        player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.6, 0.4, 0.05);
            }
        }

        telegraphRing(ctx.world(), ctx.boss().getPos(), cfg.radius, ParticleTypes.END_ROD);
        ctx.world().playSound(null, ctx.boss().getX(), ctx.boss().getY(), ctx.boss().getZ(),
                SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.HOSTILE, 1.2f, 0.5f);
    }

    private boolean hasOvertunedBuff(PlayerEntity player) {
        return player.getStatusEffects().stream().anyMatch(this::isOvertunedBuff);
    }

    private boolean isOvertunedBuff(StatusEffectInstance effect) {
        return effect.getEffectType().value().getCategory() == StatusEffectCategory.BENEFICIAL
                && effect.getAmplifier() + 1 >= ModConfig.get().purgeWave.minBuffLevel;
    }
}

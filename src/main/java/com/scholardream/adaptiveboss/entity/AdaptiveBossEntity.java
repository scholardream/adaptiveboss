package com.scholardream.adaptiveboss.entity;

import com.scholardream.adaptiveboss.skill.SkillScheduler;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The adaptive boss.
 *
 * <p>Skills, model and stats are hand-designed; the AI only learns WHEN to use
 * WHICH skill against WHICH kind of player. Week 2: skill system online —
 * charge / area slam / projectile volley, driven by a pluggable DecisionPolicy
 * (RandomPolicy for now, Python bridge in week 3).
 */
public class AdaptiveBossEntity extends HostileEntity implements GeoEntity {
    private static final RawAnimation IDLE_ANIM =
            RawAnimation.begin().then("animation.adaptive_boss.idle", Animation.LoopType.LOOP);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final SkillScheduler skillScheduler;

    protected AdaptiveBossEntity(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
        this.experiencePoints = 500;
        this.skillScheduler = new SkillScheduler(this);
    }

    public static DefaultAttributeContainer.Builder createAdaptiveBossAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 500.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 12.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
                .add(EntityAttributes.GENERIC_ARMOR, 8.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.9)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
    }

    public SkillScheduler getSkillScheduler() {
        return skillScheduler;
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.1, false));
        this.goalSelector.add(7, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(8, new LookAtEntityGoal(this, PlayerEntity.class, 24.0f));

        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        this.skillScheduler.tick();
    }

    // ---- GeckoLib ----------------------------------------------------------
    // Placeholder controller: idle only. Per-skill telegraph/cast animations
    // hook in here once the Blockbench model is ready.

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, state -> {
            state.getController().setAnimation(IDLE_ANIM);
            return PlayState.CONTINUE;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }
}

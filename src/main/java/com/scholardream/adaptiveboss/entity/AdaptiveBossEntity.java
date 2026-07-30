package com.scholardream.adaptiveboss.entity;

import com.scholardream.adaptiveboss.bridge.PlayerBehaviorTracker;
import com.scholardream.adaptiveboss.config.ModConfig;
import com.scholardream.adaptiveboss.log.FightLogger;
import com.scholardream.adaptiveboss.skill.SkillScheduler;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;


public class AdaptiveBossEntity extends HostileEntity implements GeoEntity {
    private static final RawAnimation IDLE_ANIM =
            RawAnimation.begin().then("animation.adaptive_boss.idle", Animation.LoopType.LOOP);

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final PlayerBehaviorTracker behaviorTracker = new PlayerBehaviorTracker(this);
    private final SkillScheduler skillScheduler;

    /** Active fight log session, null outside combat. */
    private FightLogger fightLogger;
    /** Ticks the target has been continuously missing; 200 ticks ends the fight as a disengage. */
    private int disengageTicks = 0;

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

    public PlayerBehaviorTracker getBehaviorTracker() {
        return behaviorTracker;
    }

    public FightLogger getFightLogger() {
        return fightLogger;
    }

    /**
     * Anti-burst mechanic: a single hit can never take more than
     * {@code boss.maxDamagePerHitFraction} of max health (default 5%).
     */
    @Override
    protected float modifyAppliedDamage(DamageSource source, float amount) {
        float capped = Math.min(amount, getMaxHealth() * ModConfig.get().boss.maxDamagePerHitFraction);
        return super.modifyAppliedDamage(source, capped);
    }

    // ---- fight log session -------------------------------------------------

    private void tickFightLogger() {
        if (!(getWorld() instanceof ServerWorld world)) {
            return;
        }
        LivingEntity target = getTarget();
        if (fightLogger == null) {
            // fight starts the moment the boss locks onto a player
            if (target instanceof PlayerEntity player && player.isAlive()) {
                fightLogger = FightLogger.start(this, world, player);
                disengageTicks = 0;
            }
            return;
        }
        if (fightLogger.getTarget().isDead()) {
            endFight(FightLogger.WINNER_BOSS); // the tracked player died
        } else if (target == null) {
            if (++disengageTicks >= 200) {
                endFight(FightLogger.WINNER_NONE); // walked away: disengage
            }
        } else {
            disengageTicks = 0;
        }
    }

    private void endFight(String winner) {
        if (fightLogger != null) {
            fightLogger.finish(winner);
            fightLogger = null;
            disengageTicks = 0;
        }
    }

    @Override
    public void onDeath(DamageSource damageSource) {
        endFight(FightLogger.WINNER_PLAYER);
        super.onDeath(damageSource);
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
        if (!getWorld().isClient()) {
            this.behaviorTracker.tick();
            this.tickFightLogger();
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        // stop tracking this boss and release the bridge thread before discard
        this.behaviorTracker.unregister();
        if (!getWorld().isClient()) {
            endFight(FightLogger.WINNER_INTERRUPTED);
            this.skillScheduler.shutdown();
        }
        super.remove(reason);
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

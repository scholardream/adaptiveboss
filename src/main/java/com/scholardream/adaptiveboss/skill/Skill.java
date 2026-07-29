package com.scholardream.adaptiveboss.skill;

import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

/**
 * Base class for boss skills.
 *
 * <p>Lifecycle, driven by {@link SkillScheduler}:
 * <ol>
 *   <li>policy picks this skill → windup starts ({@link #windupTicks()} ticks)</li>
 *   <li>{@link #onWindupTick} runs every tick during windup (telegraph particles)</li>
 *   <li>windup ends → {@link #canCast} re-checked → {@link #cast} → cooldown</li>
 *   <li>if the skill needs multi-tick behavior after cast (e.g. the charge lunge),
 *       call {@link #setActiveTicks} in cast and override {@link #tickActive}</li>
 * </ol>
 *
 * All numbers come from the config file, never hard-coded here.
 */
public abstract class Skill {
    private int activeTicks = 0;

    /** Unique id, used by DecisionPolicy and (later) the Python bridge protocol. */
    public abstract String id();

    public abstract int cooldownTicks();

    /** Telegraph duration — the player's window to react and counter. */
    public abstract int windupTicks();

    /** Checked both when the skill is picked and again right before cast. */
    public abstract boolean canCast(SkillContext ctx);

    public abstract void cast(SkillContext ctx);

    /** Telegraph hook: warning particles so the player can see the windup. */
    public void onWindupTick(SkillContext ctx, int ticksRemaining) {
    }

    // ---- post-cast active phase (opt-in) -----------------------------------

    public final boolean isActive() {
        return activeTicks > 0;
    }

    public void tickActive(SkillContext ctx) {
        activeTicks--;
    }

    protected final void setActiveTicks(int ticks) {
        this.activeTicks = ticks;
    }

    // ---- telegraph helpers --------------------------------------------------

    /** A ring of particles on the ground around a center point. */
    protected static void telegraphRing(ServerWorld world, Vec3d center, double radius, ParticleEffect particle) {
        int points = Math.max(12, (int) (radius * 6));
        for (int i = 0; i < points; i++) {
            double angle = 2.0 * Math.PI * i / points;
            double x = center.x + Math.cos(angle) * radius;
            double z = center.z + Math.sin(angle) * radius;
            world.spawnParticles(particle, x, center.y + 0.15, z, 1, 0, 0.02, 0, 0);
        }
    }

    /** A line of particles between two points (e.g. charge direction warning). */
    protected static void telegraphLine(ServerWorld world, Vec3d from, Vec3d to, ParticleEffect particle) {
        double length = from.distanceTo(to);
        int points = Math.max(2, (int) (length * 2));
        for (int i = 0; i <= points; i++) {
            Vec3d p = from.lerp(to, i / (double) points);
            world.spawnParticles(particle, p.x, p.y + 0.15, p.z, 1, 0, 0.02, 0, 0);
        }
    }
}

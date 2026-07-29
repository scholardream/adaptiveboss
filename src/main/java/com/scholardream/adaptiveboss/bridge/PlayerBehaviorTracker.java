package com.scholardream.adaptiveboss.bridge;

import com.google.gson.JsonObject;
import com.scholardream.adaptiveboss.entity.AdaptiveBossEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.PotionItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rolling 5-second (100 tick) summary of how the boss's current target plays:
 * melee swings on the boss, projectiles fired, potions drunk, and a movement
 * direction histogram. The result feeds the {@code behavior} section of the
 * bridge state JSON.
 *
 * <p>One tracker per boss. Event callbacks are registered globally, so the
 * tracker filters hard: it only counts events from the player it currently
 * tracks, and it re-binds (wiping the window) whenever the boss's target
 * changes. {@link #unregister()} is called when the boss is discarded.
 */
public class PlayerBehaviorTracker {
    /** Window length: 5 s = 100 ticks. */
    public static final int WINDOW_TICKS = 100;
    private static final int SAMPLE_INTERVAL = 5;
    private static final int MAX_SAMPLES = WINDOW_TICKS / SAMPLE_INTERVAL;
    /** Horizontal speed below this (blocks/tick) counts as STILL. */
    private static final double STILL_SPEED = 0.05;
    /** Vanilla potion drink duration. */
    private static final int POTION_USE_TICKS = 32;
    private static final String[] DIRECTIONS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    /** Trackers of living bosses, so global callbacks can route events. */
    private static final Set<PlayerBehaviorTracker> ACTIVE = ConcurrentHashMap.newKeySet();

    private final AdaptiveBossEntity boss;

    private UUID trackedPlayerId;
    private final ArrayDeque<Long> meleeEvents = new ArrayDeque<>();
    private final ArrayDeque<Long> rangedEvents = new ArrayDeque<>();
    private final ArrayDeque<Long> potionEvents = new ArrayDeque<>();
    private final ArrayDeque<String> moveSamples = new ArrayDeque<>();

    private boolean drinkingPotion = false;
    private long drinkStartTime = 0;
    private boolean registered = false;

    public PlayerBehaviorTracker(AdaptiveBossEntity boss) {
        this.boss = boss;
    }

    /** Registered once from the mod initializer; routes global events to the right boss. */
    public static void registerGlobalCallbacks() {
        // melee: victim is the boss, so no registry lookup is needed
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient() && entity instanceof AdaptiveBossEntity boss) {
                boss.getBehaviorTracker().onMeleeAttack(player, world.getTime());
            }
            return ActionResult.PASS;
        });
        // ranged: count actual projectile spawns owned by a tracked player
        // (robust across bow / crossbow / trident; no reliance on use-start events)
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity instanceof ProjectileEntity projectile
                    && projectile.getOwner() instanceof PlayerEntity owner) {
                for (PlayerBehaviorTracker tracker : ACTIVE) {
                    tracker.onProjectileFired(owner, world.getTime());
                }
            }
        });
    }

    public void register() {
        if (!registered) {
            registered = true;
            ACTIVE.add(this);
        }
    }

    public void unregister() {
        registered = false;
        ACTIVE.remove(this);
    }

    /** Called every server tick from the boss entity. */
    public void tick() {
        World world = boss.getWorld();
        if (world.isClient()) {
            return;
        }
        register();
        long now = world.getTime();

        LivingEntity target = boss.getTarget();
        PlayerEntity player = target instanceof PlayerEntity p ? p : null;
        UUID id = player == null ? null : player.getUuid();
        if (!Objects.equals(id, trackedPlayerId)) {
            // target switched (or lost): wipe the window so stale fights don't leak in
            resetWindow();
            trackedPlayerId = id;
        }
        evictOld(now);

        if (player == null) {
            return;
        }
        if (now % SAMPLE_INTERVAL == 0) {
            sampleMovement(player);
        }
        trackPotionDrinking(player, now);
    }

    public void onMeleeAttack(PlayerEntity attacker, long now) {
        if (trackedPlayerId != null && trackedPlayerId.equals(attacker.getUuid())) {
            meleeEvents.addLast(now);
        }
    }

    public void onProjectileFired(PlayerEntity owner, long now) {
        if (trackedPlayerId != null && trackedPlayerId.equals(owner.getUuid())) {
            rangedEvents.addLast(now);
        }
    }

    /** The {@code behavior} section of the bridge state JSON. */
    public JsonObject snapshotJson() {
        JsonObject behavior = new JsonObject();
        behavior.addProperty("melee_attacks_5s", meleeEvents.size());
        behavior.addProperty("ranged_attacks_5s", rangedEvents.size());
        behavior.addProperty("potion_drinks_5s", potionEvents.size());

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String direction : DIRECTIONS) {
            counts.put(direction, 0);
        }
        counts.put("STILL", 0);
        for (String sample : moveSamples) {
            counts.merge(sample, 1, Integer::sum);
        }
        JsonObject histogram = new JsonObject();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            histogram.addProperty(entry.getKey(), entry.getValue());
        }
        behavior.add("move_histogram_5s", histogram);
        return behavior;
    }

    private void sampleMovement(PlayerEntity player) {
        Vec3d velocity = player.getVelocity();
        double speed = Math.hypot(velocity.x, velocity.z);
        String bucket;
        if (speed < STILL_SPEED) {
            bucket = "STILL";
        } else {
            // 0 deg = north (-Z), 90 deg = east (+X)
            double degrees = Math.toDegrees(Math.atan2(velocity.x, -velocity.z));
            int index = (int) Math.round(degrees / 45.0);
            bucket = DIRECTIONS[((index % 8) + 8) % 8];
        }
        moveSamples.addLast(bucket);
        while (moveSamples.size() > MAX_SAMPLES) {
            moveSamples.removeFirst();
        }
    }

    /**
     * Counts a drink only when the use actually completes (full 32-tick use);
     * releasing right-click early cancels the drink and is not counted.
     */
    private void trackPotionDrinking(PlayerEntity player, long now) {
        boolean usingPotion = player.isUsingItem() && player.getActiveItem().getItem() instanceof PotionItem;
        if (usingPotion && !drinkingPotion) {
            drinkingPotion = true;
            drinkStartTime = now;
        } else if (!usingPotion && drinkingPotion) {
            drinkingPotion = false;
            if (now - drinkStartTime >= POTION_USE_TICKS - 1) {
                potionEvents.addLast(now);
            }
        }
    }

    private void resetWindow() {
        meleeEvents.clear();
        rangedEvents.clear();
        potionEvents.clear();
        moveSamples.clear();
        drinkingPotion = false;
    }

    private void evictOld(long now) {
        long cutoff = now - WINDOW_TICKS;
        evictBefore(meleeEvents, cutoff);
        evictBefore(rangedEvents, cutoff);
        evictBefore(potionEvents, cutoff);
    }

    private static void evictBefore(ArrayDeque<Long> events, long cutoff) {
        while (!events.isEmpty() && events.peekFirst() < cutoff) {
            events.removeFirst();
        }
    }
}

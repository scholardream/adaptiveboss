package com.scholardream.adaptiveboss.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.scholardream.adaptiveboss.AdaptiveBossMod;
import com.scholardream.adaptiveboss.config.ModConfig;
import com.scholardream.adaptiveboss.skill.DecisionPolicy;
import com.scholardream.adaptiveboss.skill.RandomPolicy;
import com.scholardream.adaptiveboss.skill.Skill;
import com.scholardream.adaptiveboss.skill.SkillContext;
import com.scholardream.adaptiveboss.skill.SkillScheduler;
import net.minecraft.entity.player.PlayerEntity;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Week 3 decision policy: asks the Python service which skill to use.
 *
 * <p>The game thread never touches the socket — it hands a serialized state
 * line to {@link BridgeClient} and waits at most {@code bridge.timeoutMs} for
 * the reply. Bridge down, reply timeout, dropped connection or a bogus skill
 * id all degrade transparently to an internal {@link RandomPolicy}, so the
 * boss never stands still. An explicit {@code {"action": null}} reply is a
 * valid "keep chasing" decision and is honored as-is.
 */
public class SocketPolicy implements DecisionPolicy, AutoCloseable {
    private static final Gson GSON = new Gson();

    private final SkillScheduler scheduler;
    private final PlayerBehaviorTracker behaviorTracker;
    private final BridgeClient bridge;
    private final DecisionPolicy fallback = new RandomPolicy();

    /** Last reply metadata, for debug display (e.g. a future /boss_status command). */
    private volatile String lastAction;
    private volatile String lastReason;

    public SocketPolicy(SkillScheduler scheduler, PlayerBehaviorTracker behaviorTracker) {
        this.scheduler = scheduler;
        this.behaviorTracker = behaviorTracker;
        ModConfig.Bridge cfg = ModConfig.get().bridge;
        this.bridge = new BridgeClient(cfg.host, cfg.port, cfg.timeoutMs);
        // only server-side bosses talk to Python; client entities stay purely local
        if (cfg.enabled && !scheduler.getBoss().getWorld().isClient()) {
            bridge.start();
        }
    }

    public boolean isBridgeAvailable() {
        return bridge.isAvailable();
    }

    public String getLastAction() {
        return lastAction;
    }

    public String getLastReason() {
        return lastReason;
    }

    @Override
    public String chooseSkill(SkillContext context, List<Skill> availableSkills) {
        ModConfig.Bridge cfg = ModConfig.get().bridge;
        if (!cfg.enabled || !bridge.isAvailable() || !(context.target() instanceof PlayerEntity)) {
            return fallback.chooseSkill(context, availableSkills);
        }

        String json = BattleStateJson.toJson(context, availableSkills, behaviorTracker, scheduler);
        CompletableFuture<String> future = bridge.submit(json);
        if (future == null) {
            return fallback.chooseSkill(context, availableSkills);
        }

        String reply;
        try {
            reply = future.get(cfg.timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // python is wedged: drop the socket so the next decision starts clean
            bridge.dropConnection("reply timeout after " + cfg.timeoutMs + "ms");
            return fallback.chooseSkill(context, availableSkills);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback.chooseSkill(context, availableSkills);
        } catch (ExecutionException e) {
            return fallback.chooseSkill(context, availableSkills);
        }
        if (reply == null) {
            // connection dropped mid-flight; the background thread is already reconnecting
            return fallback.chooseSkill(context, availableSkills);
        }

        String action;
        try {
            JsonObject replyJson = GSON.fromJson(reply, JsonObject.class);
            action = replyJson.has("action") && !replyJson.get("action").isJsonNull()
                    ? replyJson.get("action").getAsString()
                    : null;
            lastReason = replyJson.has("reason") && !replyJson.get("reason").isJsonNull()
                    ? replyJson.get("reason").getAsString()
                    : "";
        } catch (Exception e) {
            AdaptiveBossMod.LOGGER.warn("[AdaptiveBoss] malformed bridge reply: {}", reply);
            return fallback.chooseSkill(context, availableSkills);
        }

        if (action == null) {
            lastAction = null;
            return null; // valid decision: keep basic melee/chase
        }
        for (Skill skill : availableSkills) {
            if (skill.id().equals(action)) {
                lastAction = action;
                return action;
            }
        }
        AdaptiveBossMod.LOGGER.warn("[AdaptiveBoss] bridge returned unknown/unavailable skill '{}', ignoring", action);
        return fallback.chooseSkill(context, availableSkills);
    }

    @Override
    public void close() {
        bridge.close();
    }
}

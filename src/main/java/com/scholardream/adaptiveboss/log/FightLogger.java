package com.scholardream.adaptiveboss.log;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.scholardream.adaptiveboss.AdaptiveBossMod;
import com.scholardream.adaptiveboss.config.ModConfig;
import com.scholardream.adaptiveboss.entity.AdaptiveBossEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One fight's combat log: a single NDJSON file under
 * {@code <world>/adaptive_boss_logs/}.
 *
 * <p>Layout: one {@code meta} line, one {@code frame} line per decision
 * (same 5-tick cadence as the Python bridge), one {@code summary} line at
 * the end. The game thread only builds JSON strings and hands them to
 * {@link FightLogWriter}; all file IO happens on the writer thread.
 *
 * <p>Lifetime is managed by {@link AdaptiveBossEntity}: started when the boss
 * acquires a player target, finished on boss death, target death, 200-tick
 * disengage, or mid-fight removal ("interrupted").
 */
public class FightLogger {
    /** Winner values written to the summary line. */
    public static final String WINNER_BOSS = "boss";
    public static final String WINNER_PLAYER = "player";
    /** No winner: the target walked away for 200 ticks. */
    public static final String WINNER_NONE = "none";
    /** No winner: the boss was discarded mid-fight (despawn, unload, /kill without death). */
    public static final String WINNER_INTERRUPTED = "interrupted";

    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final AdaptiveBossEntity boss;
    private final PlayerEntity target;
    private final String fightId;
    private final Path file;
    private final long startTick;

    private int frame = 0;
    private float bossHurt = 0.0f;
    private float playerHurt = 0.0f;
    private final Map<String, Integer> skillUsage = new LinkedHashMap<>();
    private boolean finished = false;

    private FightLogger(AdaptiveBossEntity boss, PlayerEntity target, String fightId, Path file, long startTick) {
        this.boss = boss;
        this.target = target;
        this.fightId = fightId;
        this.file = file;
        this.startTick = startTick;
    }

    /** Creates the session and writes the meta line. Called from the server thread. */
    public static FightLogger start(AdaptiveBossEntity boss, ServerWorld world, PlayerEntity target) {
        String timestamp = LocalDateTime.now().format(FILE_TIME);
        String uuid8 = boss.getUuidAsString().substring(0, 8);
        String fightId = timestamp + "_" + uuid8;
        // works for both integrated (saves/<level>) and dedicated (<level>) servers
        Path file = world.getServer().getSavePath(WorldSavePath.ROOT)
                .resolve("adaptive_boss_logs")
                .resolve("fight_" + fightId + ".ndjson");

        FightLogger logger = new FightLogger(boss, target, fightId, file, world.getTime());
        boss.getBehaviorTracker().resetCumulative();

        JsonObject meta = new JsonObject();
        meta.addProperty("type", "meta");
        meta.addProperty("fight_id", fightId);
        meta.addProperty("start_time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        meta.addProperty("world", world.getServer().getSaveProperties().getLevelName());
        meta.addProperty("dimension", world.getRegistryKey().getValue().toString());
        meta.addProperty("difficulty", world.getDifficulty().getName());
        meta.addProperty("boss_uuid", boss.getUuidAsString());
        meta.addProperty("target_uuid", target.getUuidAsString());
        FightLogWriter.submit(file, meta.toString());

        AdaptiveBossMod.LOGGER.info("[AdaptiveBoss] fight {} started, logging to {}", fightId, file);
        return logger;
    }

    public PlayerEntity getTarget() {
        return target;
    }

    /**
     * Routes global damage events to the fight they belong to. Registered once
     * from the mod initializer. Uses ALLOW_DAMAGE (this Fabric API build has
     * no AFTER_DAMAGE), so amounts are pre-armor; the boss side is manually
     * capped to match {@code modifyAppliedDamage}.
     */
    public static void registerGlobalCallbacks() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (amount <= 0) {
                return true;
            }
            // boss takes a hit from its target player
            if (entity instanceof AdaptiveBossEntity boss
                    && source.getAttacker() instanceof PlayerEntity) {
                FightLogger logger = boss.getFightLogger();
                if (logger != null) {
                    logger.bossHurt += Math.min(amount,
                            boss.getMaxHealth() * ModConfig.get().boss.maxDamagePerHitFraction);
                }
                return true;
            }
            // the boss's target player takes a hit from the boss
            if (entity instanceof PlayerEntity player
                    && source.getAttacker() instanceof AdaptiveBossEntity boss) {
                FightLogger logger = boss.getFightLogger();
                if (logger != null && logger.target == player) {
                    logger.playerHurt += amount;
                }
            }
            return true;
        });
    }

    /**
     * Appends one decision frame. {@code state} comes from
     * {@code BattleStateJson.toJsonObject} — same snapshot the bridge sends.
     * Damage accumulated since the previous frame is attached, then reset.
     */
    public void logFrame(JsonObject state, String action, String source) {
        if (finished) {
            return;
        }
        JsonObject line = new JsonObject();
        line.addProperty("type", "frame");
        line.addProperty("frame", frame++);
        line.add("state", state);
        line.add("action", action == null ? JsonNull.INSTANCE : new JsonPrimitive(action));
        line.addProperty("source", source);
        line.addProperty("boss_hurt", bossHurt);
        line.addProperty("player_hurt", playerHurt);
        bossHurt = 0.0f;
        playerHurt = 0.0f;
        FightLogWriter.submit(file, line.toString());
    }

    /** Counts one actual cast (after the windup completes), not one decision. */
    public void recordSkillUse(String skillId) {
        skillUsage.merge(skillId, 1, Integer::sum);
    }

    /** Writes the summary line and closes the file. Idempotent. */
    public void finish(String winner) {
        if (finished) {
            return;
        }
        finished = true;

        JsonObject line = new JsonObject();
        line.addProperty("type", "summary");
        line.addProperty("fight_id", fightId);
        line.addProperty("winner", winner);
        line.addProperty("duration_ticks", boss.getWorld().getTime() - startTick);
        line.add("behavior", boss.getBehaviorTracker().cumulativeSnapshotJson());

        JsonObject skills = new JsonObject();
        for (Map.Entry<String, Integer> entry : skillUsage.entrySet()) {
            skills.addProperty(entry.getKey(), entry.getValue());
        }
        line.add("skill_usage", skills);
        line.addProperty("boss_hp_remaining", boss.getHealth());
        line.addProperty("player_hp_remaining", target.getHealth());

        FightLogWriter.submit(file, line.toString());
        FightLogWriter.closeFile(file);
        AdaptiveBossMod.LOGGER.info("[AdaptiveBoss] fight {} ended (winner: {})", fightId, winner);
    }
}

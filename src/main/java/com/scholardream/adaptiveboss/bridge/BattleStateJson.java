package com.scholardream.adaptiveboss.bridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.scholardream.adaptiveboss.entity.AdaptiveBossEntity;
import com.scholardream.adaptiveboss.skill.Skill;
import com.scholardream.adaptiveboss.skill.SkillContext;
import com.scholardream.adaptiveboss.skill.SkillScheduler;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Serializes the per-decision fight snapshot into the NDJSON line the Python
 * service expects. Field names are the protocol contract — they must match
 * the dataclasses in {@code python/decision_server.py} one to one.
 */
public final class BattleStateJson {
    private BattleStateJson() {
    }

    public static String toJson(SkillContext ctx, List<Skill> available,
                                PlayerBehaviorTracker behavior, SkillScheduler scheduler) {
        return toJsonObject(ctx, available, behavior, scheduler).toString();
    }

    /**
     * The same snapshot as a {@link JsonObject}, for callers that embed the
     * state into a larger document (e.g. the fight logger's frame lines)
     * instead of sending it over the wire.
     */
    public static JsonObject toJsonObject(SkillContext ctx, List<Skill> available,
                                          PlayerBehaviorTracker behavior, SkillScheduler scheduler) {
        AdaptiveBossEntity boss = ctx.boss();
        PlayerEntity player = (PlayerEntity) ctx.target();

        JsonObject root = new JsonObject();
        root.addProperty("tick", ctx.world().getTime());

        JsonObject bossJson = new JsonObject();
        bossJson.addProperty("hp", boss.getHealth());
        bossJson.addProperty("max_hp", boss.getMaxHealth());
        bossJson.addProperty("x", boss.getX());
        bossJson.addProperty("y", boss.getY());
        bossJson.addProperty("z", boss.getZ());
        bossJson.addProperty("yaw", boss.getYaw());
        root.add("boss", bossJson);

        JsonObject playerJson = new JsonObject();
        playerJson.addProperty("uuid", player.getUuidAsString());
        playerJson.addProperty("hp", player.getHealth());
        playerJson.addProperty("max_hp", player.getMaxHealth());
        playerJson.addProperty("x", player.getX());
        playerJson.addProperty("y", player.getY());
        playerJson.addProperty("z", player.getZ());
        Vec3d velocity = player.getVelocity();
        playerJson.addProperty("vx", velocity.x);
        playerJson.addProperty("vy", velocity.y);
        playerJson.addProperty("vz", velocity.z);
        playerJson.addProperty("held_item",
                Registries.ITEM.getId(player.getMainHandStack().getItem()).toString());
        JsonArray effects = new JsonArray();
        for (StatusEffectInstance effect : player.getStatusEffects()) {
            effects.add(Registries.STATUS_EFFECT.getId(effect.getEffectType().value()).toString());
        }
        playerJson.add("potion_effects", effects);
        root.add("player", playerJson);

        root.addProperty("distance", ctx.distanceToTarget());
        root.add("behavior", behavior.snapshotJson());

        JsonObject cooldowns = new JsonObject();
        for (Skill skill : scheduler.getSkills()) {
            cooldowns.addProperty(skill.id(), scheduler.getCooldownRemaining(skill.id()));
        }
        root.add("cooldowns", cooldowns);

        JsonArray availableSkills = new JsonArray();
        for (Skill skill : available) {
            availableSkills.add(skill.id());
        }
        root.add("available_skills", availableSkills);

        return root;
    }
}

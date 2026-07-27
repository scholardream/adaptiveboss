package com.scholardream.adaptiveboss.command;

import com.mojang.brigadier.context.CommandContext;
import com.scholardream.adaptiveboss.entity.AdaptiveBossEntity;
import com.scholardream.adaptiveboss.entity.ModEntities;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.literal;

public final class ModCommands {
    private ModCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(literal("summon_adaptive_boss")
                        .requires(source -> source.hasPermissionLevel(2))
                        .executes(ModCommands::summonAtExecutor)));
    }

    private static int summonAtExecutor(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerWorld world = source.getWorld();

        AdaptiveBossEntity boss = ModEntities.ADAPTIVE_BOSS.create(world);
        if (boss == null) {
            source.sendError(Text.literal("Failed to create the Adaptive Boss"));
            return 0;
        }

        boss.refreshPositionAndAngles(source.getPosition(), source.getRotation().y, 0.0f);
        world.spawnEntity(boss);
        source.sendFeedback(() -> Text.literal("Summoned Adaptive Boss (" + boss.getUuidAsString() + ")"), true);
        return 1;
    }
}

package com.scholardream.adaptiveboss;

import com.scholardream.adaptiveboss.bridge.PlayerBehaviorTracker;
import com.scholardream.adaptiveboss.command.ModCommands;
import com.scholardream.adaptiveboss.config.ModConfig;
import com.scholardream.adaptiveboss.entity.AdaptiveBossEntity;
import com.scholardream.adaptiveboss.entity.ModEntities;
import com.scholardream.adaptiveboss.log.FightLogger;
import com.scholardream.adaptiveboss.log.FightLogWriter;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdaptiveBossMod implements ModInitializer {
    public static final String MOD_ID = "adaptiveboss";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModConfig.load();
        ModEntities.register();
        FabricDefaultAttributeRegistry.register(ModEntities.ADAPTIVE_BOSS, AdaptiveBossEntity.createAdaptiveBossAttributes());
        ModCommands.register();
        PlayerBehaviorTracker.registerGlobalCallbacks();
        FightLogger.registerGlobalCallbacks();
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> FightLogWriter.shutdown());
        LOGGER.info("[AdaptiveBoss] initialized — skills are hand-designed, tactics are learned.");
    }
}

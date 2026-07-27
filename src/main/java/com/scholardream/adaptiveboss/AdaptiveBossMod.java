package com.scholardream.adaptiveboss;

import com.scholardream.adaptiveboss.command.ModCommands;
import com.scholardream.adaptiveboss.entity.AdaptiveBossEntity;
import com.scholardream.adaptiveboss.entity.ModEntities;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdaptiveBossMod implements ModInitializer {
    public static final String MOD_ID = "adaptiveboss";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModEntities.register();
        FabricDefaultAttributeRegistry.register(ModEntities.ADAPTIVE_BOSS, AdaptiveBossEntity.createAdaptiveBossAttributes());
        ModCommands.register();
        LOGGER.info("[AdaptiveBoss] initialized — skills are hand-designed, tactics are learned.");
    }
}

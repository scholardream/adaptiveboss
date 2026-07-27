package com.scholardream.adaptiveboss;

import com.scholardream.adaptiveboss.client.AdaptiveBossRenderer;
import com.scholardream.adaptiveboss.entity.ModEntities;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

public class AdaptiveBossClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(ModEntities.ADAPTIVE_BOSS, AdaptiveBossRenderer::new);
    }
}

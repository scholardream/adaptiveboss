package com.scholardream.adaptiveboss.client;

import com.scholardream.adaptiveboss.entity.AdaptiveBossEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class AdaptiveBossRenderer extends GeoEntityRenderer<AdaptiveBossEntity> {
    public AdaptiveBossRenderer(EntityRendererFactory.Context context) {
        super(context, new AdaptiveBossModel());
        this.shadowRadius = 0.9f;
    }
}

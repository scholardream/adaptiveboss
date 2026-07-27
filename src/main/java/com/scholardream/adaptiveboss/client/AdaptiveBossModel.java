package com.scholardream.adaptiveboss.client;

import com.scholardream.adaptiveboss.AdaptiveBossMod;
import com.scholardream.adaptiveboss.entity.AdaptiveBossEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

/**
 * Points GeckoLib at the Blockbench-exported assets. Replace the files under
 * assets/adaptiveboss/{geo,animations,textures} with the real model later —
 * no Java changes needed as long as the file names stay the same.
 */
public class AdaptiveBossModel extends GeoModel<AdaptiveBossEntity> {
    @Override
    public Identifier getModelResource(AdaptiveBossEntity animatable) {
        return Identifier.of(AdaptiveBossMod.MOD_ID, "geo/adaptive_boss.geo.json");
    }

    @Override
    public Identifier getTextureResource(AdaptiveBossEntity animatable) {
        return Identifier.of(AdaptiveBossMod.MOD_ID, "textures/entity/adaptive_boss.png");
    }

    @Override
    public Identifier getAnimationResource(AdaptiveBossEntity animatable) {
        return Identifier.of(AdaptiveBossMod.MOD_ID, "animations/adaptive_boss.animation.json");
    }
}

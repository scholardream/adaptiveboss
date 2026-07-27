package com.scholardream.adaptiveboss.entity;

import com.scholardream.adaptiveboss.AdaptiveBossMod;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class ModEntities {
    public static final EntityType<AdaptiveBossEntity> ADAPTIVE_BOSS = Registry.register(
            Registries.ENTITY_TYPE,
            Identifier.of(AdaptiveBossMod.MOD_ID, "adaptive_boss"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, AdaptiveBossEntity::new)
                    .dimensions(EntityDimensions.fixed(1.2f, 3.0f))
                    .trackRangeBlocks(80)
                    .trackedUpdateRate(2)
                    .build()
    );

    private ModEntities() {
    }

    public static void register() {
        AdaptiveBossMod.LOGGER.info("[AdaptiveBoss] registering entities");
    }
}

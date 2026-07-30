package com.scholardream.adaptiveboss.client;

import com.scholardream.adaptiveboss.entity.AdaptiveBossEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Renders Vealorny with an emissive glowmask plus the analysis core's
 * red -> green -> blue color cycle.
 *
 * <p>The glow layer is picked up automatically by {@link AutoGlowingGeoLayer}:
 * it looks for {@code adaptive_boss_glowmask.png} next to the base texture.
 * The visor slit ({@code #9FD8FF} on the glowmask, head bone) glows constantly
 * and is never tinted.
 *
 * <p>The core cycle is implemented by tinting the {@code coreFloat}, {@code moduleL}
 * and {@code moduleR} bones in {@link #renderRecursively}, which runs for BOTH the
 * base pass and the glow-layer pass. Main-texture core pixels must be white-ish and
 * glowmask core pixels pure white for the tint to show true; module glowmask pixels
 * are ~60% grey, which naturally dims their tint (see PHASE1_MODEL_SPEC.md §5, plan A).
 */
public class AdaptiveBossRenderer extends GeoEntityRenderer<AdaptiveBossEntity> {
    /** One full red->green->blue cycle: 120 ticks = 6 seconds. */
    private static final float CORE_CYCLE_TICKS = 120.0f;

    public AdaptiveBossRenderer(EntityRendererFactory.Context context) {
        super(context, new AdaptiveBossModel());
        this.shadowRadius = 0.9f;
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    /** ARGB tint for the analysis core at the current moment. */
    private static int coreColor(AdaptiveBossEntity animatable, float partialTick) {
        float hue = ((animatable.getWorld().getTime() + partialTick) % CORE_CYCLE_TICKS) / CORE_CYCLE_TICKS;
        return 0xFF000000 | MathHelper.hsvToRgb(hue, 1.0f, 1.0f);
    }

    private static boolean isCoreBone(String boneName) {
        return boneName.equals("coreFloat") || boneName.equals("moduleL") || boneName.equals("moduleR");
    }

    @Override
    public void renderRecursively(MatrixStack poseStack, AdaptiveBossEntity animatable, GeoBone bone,
                                  RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        if (isCoreBone(bone.getName())) {
            colour = coreColor(animatable, partialTick);
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender,
                partialTick, packedLight, packedOverlay, colour);
    }
}

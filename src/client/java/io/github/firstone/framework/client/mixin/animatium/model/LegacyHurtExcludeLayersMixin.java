package io.github.firstone.framework.client.mixin.animatium.model;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.firstone.framework.features.animatium.model.LegacyHurtTint;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.layers.StuckInBodyLayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps the 1.7.10 red hurt pass off the layers 1.7.10 drew outside it: skulls and blocks on heads
 * ({@code CustomHeadLayer}), capes, elytra, stuck arrows/stingers and shoulder parrots
 *
 * <p>Capturing is suspended ({@link LegacyHurtTint#suspend()}) while {@code LivingEntityRenderer.render} draws one
 * of these layers. The layer is recognized by its class where the renderer calls it, as their own {@code render}
 * methods do not share one name in production. Items and blocks have their own mixins
 * ({@link LegacyHurtExcludeItemsMixin}, {@link LegacyHurtExcludeBlocksMixin}).</p>
 */
@Mixin(LivingEntityRenderer.class)
public class LegacyHurtExcludeLayersMixin {

    @WrapOperation(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/Entity;FFFFFF)V"))
    private void animatium$excludeLayer(RenderLayer<?, ?> layer, PoseStack poseStack, MultiBufferSource buffers,
                                        int light, Entity entity, float limbSwing, float limbSwingAmount,
                                        float partialTick, float ageInTicks, float headYaw, float headPitch,
                                        Operation<Void> original) {
        boolean excluded = layer instanceof CustomHeadLayer<?, ?> || layer instanceof CapeLayer
            || layer instanceof ElytraLayer<?, ?> || layer instanceof StuckInBodyLayer<?, ?>
            || layer instanceof ParrotOnShoulderLayer<?>;
        if (excluded) {
            LegacyHurtTint.suspend();
        }
        try {
            original.call(layer, poseStack, buffers, light, entity, limbSwing, limbSwingAmount, partialTick,
                ageInTicks, headYaw, headPitch);
        } finally {
            if (excluded) {
                LegacyHurtTint.resume();
            }
        }
    }
}

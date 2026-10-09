package io.github.firstone.framework.client.mixin.animatium.model;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.firstone.framework.features.animatium.LegacyHurtTint;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks the 1.7.10 hurt tint ({@link LegacyHurtTint}) into living entity rendering
 *
 * <ul>
 *   <li>{@code render} HEAD: a hurt entity gets collecting buffers, so the model, its layers and armor are copied.</li>
 *   <li>{@code render} RETURN: the red pass is drawn once over the copied geometry.</li>
 *   <li>{@code getOverlayCoords}: no longer reports "hurt", which removes the 1.21.1 overlay red (the creeper's white
 *       flash is kept).</li>
 * </ul>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LegacyHurtTintMixin {

    @ModifyVariable(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), argsOnly = true)
    private MultiBufferSource animatium$beginHurtTint(MultiBufferSource buffers, LivingEntity entity) {
        return LegacyHurtTint.begin(entity, buffers);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("RETURN"))
    private void animatium$endHurtTint(LivingEntity entity, float yaw, float partialTick, PoseStack poseStack,
                                       MultiBufferSource buffers, int light, CallbackInfo ci) {
        LegacyHurtTint.end(entity);
    }

    @ModifyReturnValue(method = "getOverlayCoords", at = @At("RETURN"))
    private static int animatium$noVanillaHurtRed(int overlay, LivingEntity entity, float whiteness) {
        return LegacyHurtTint.enabled() ? OverlayTexture.pack(OverlayTexture.u(whiteness), OverlayTexture.v(false)) : overlay;
    }
}

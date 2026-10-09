package io.github.firstone.framework.client.mixin.animatium.model;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.firstone.framework.features.animatium.LegacySneakPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Tilts the cape (+25°) whenever the player is drawn sneaking, as 1.7.10 {@code RenderPlayer} did with the sneak flag
 */
@Mixin(CapeLayer.class)
public class LegacySneakCapeMixin {

    @ModifyExpressionValue(
        method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/player/AbstractClientPlayer;FFFFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isCrouching()Z")
    )
    private boolean animatium$legacySneakCape(boolean crouching, PoseStack poseStack, MultiBufferSource buffers, int light,
                                              AbstractClientPlayer player) {
        return LegacySneakPose.enabled() ? LegacySneakPose.isSneaking(player) : crouching;
    }
}

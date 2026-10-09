package io.github.firstone.framework.client.mixin.animatium.model;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.firstone.framework.features.animatium.LegacySneakPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Decides when and how low a player is drawn sneaking, like 1.7.10 ({@link LegacySneakPose})
 *
 * <ul>
 *   <li>{@code setModelProperties}: the model is crouched whenever 1.7.10 would draw it sneaking (also while flying).</li>
 *   <li>{@code getRenderOffset}: other players are lowered 0.125 block; the local player by its 1.7.10 eye offset.</li>
 * </ul>
 */
@Mixin(PlayerRenderer.class)
public class LegacySneakRendererMixin {

    @ModifyExpressionValue(
        method = "setModelProperties",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;isCrouching()Z")
    )
    private boolean animatium$legacySneakModel(boolean crouching, AbstractClientPlayer player) {
        return LegacySneakPose.enabled() ? LegacySneakPose.isSneaking(player) : crouching;
    }

    @ModifyReturnValue(
        method = "getRenderOffset(Lnet/minecraft/client/player/AbstractClientPlayer;F)Lnet/minecraft/world/phys/Vec3;",
        at = @At("RETURN")
    )
    private Vec3 animatium$legacySneakOffset(Vec3 offset, AbstractClientPlayer player, float partialTick) {
        return LegacySneakPose.enabled() ? LegacySneakPose.renderOffset(player, partialTick) : offset;
    }
}

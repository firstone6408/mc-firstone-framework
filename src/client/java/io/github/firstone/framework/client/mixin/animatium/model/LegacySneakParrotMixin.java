package io.github.firstone.framework.client.mixin.animatium.model;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.firstone.framework.features.animatium.LegacySneakPose;
import net.minecraft.client.renderer.entity.layers.ParrotOnShoulderLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps shoulder parrots on the shoulders of a 1.7.10-sneaking player
 *
 * <p>1.21.1 lowers shoulder parrots while crouching because its crouch pose lowers the body; the 1.7.10 pose does not,
 * so the standing position is used. The target is the lambda that renders one parrot ({@code method_17958}, the same
 * name in development and production).</p>
 */
@Mixin(ParrotOnShoulderLayer.class)
public class LegacySneakParrotMixin {

    @ModifyExpressionValue(
        method = "method_17958",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isCrouching()Z")
    )
    private boolean animatium$noCrouchParrotOffset(boolean crouching) {
        return !LegacySneakPose.enabled() && crouching;
    }
}

package io.github.firstone.framework.client.mixin.animatium.model;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.firstone.framework.features.animatium.LegacySneakPose;
import net.minecraft.client.renderer.entity.FishingHookRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps the fishing line attached to the hand of a 1.7.10-sneaking player
 *
 * <p>1.21.1 moves the line origin down by 0.1875 while crouching because its crouch pose lowers the arms. The 1.7.10
 * pose does not lower the arms (and 1.7.10 had no such offset), so the offset is skipped.</p>
 */
@Mixin(FishingHookRenderer.class)
public class LegacySneakAttachmentsMixin {

    @ModifyExpressionValue(
        method = "getPlayerHandPos",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isCrouching()Z")
    )
    private boolean animatium$noCrouchLineOffset(boolean crouching) {
        return !LegacySneakPose.enabled() && crouching;
    }
}

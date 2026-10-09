package io.github.firstone.framework.client.mixin.animatium.model;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.firstone.framework.features.animatium.LegacySneakPose;
import net.minecraft.client.model.PlayerModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps the cape at its standing position under the 1.7.10 sneak pose
 *
 * <p>1.21.1 moves the cape down and back while crouching because its crouch pose lowers the body. The 1.7.10 pose does
 * not lower the body; 1.7.10 only tilted the cape by 25° while sneaking, which {@link LegacySneakCapeMixin} does.</p>
 */
@Mixin(PlayerModel.class)
public class LegacySneakCloakMixin {

    @ModifyExpressionValue(
        method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isCrouching()Z")
    )
    private boolean animatium$standingCloak(boolean crouching) {
        return !LegacySneakPose.enabled() && crouching;
    }
}

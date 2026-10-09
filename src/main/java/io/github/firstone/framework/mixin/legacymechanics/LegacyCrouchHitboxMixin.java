package io.github.firstone.framework.mixin.legacymechanics;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps the full hitbox of crouching players on the server, like 1.7.10
 *
 * <p>A 1.7.10 server never changed the bounding box when sneaking (0.6 × 1.8) and always used an eye height of 1.62.
 * On the server side only ({@code !isClientSide()}), crouching players get the standing dimensions, so attacks hit
 * the full-height box and server checks use the 1.62 eye. The client side is left to Animatium.</p>
 */
@Mixin(Player.class)
public class LegacyCrouchHitboxMixin {

    @ModifyReturnValue(method = "getDefaultDimensions", at = @At("RETURN"))
    private EntityDimensions legacymechanics$standingHitboxWhileCrouching(EntityDimensions dimensions, Pose pose) {
        Player self = (Player) (Object) this;
        if (pose == Pose.CROUCHING && self.level() != null && !self.level().isClientSide()
            && LegacyMechanicsFeature.getConfig().legacyCrouchHitbox) {
            return Player.STANDING_DIMENSIONS;
        }
        return dimensions;
    }
}

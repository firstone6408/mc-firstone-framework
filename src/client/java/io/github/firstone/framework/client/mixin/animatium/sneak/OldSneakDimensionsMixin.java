package io.github.firstone.framework.client.mixin.animatium.sneak;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Keeps the standing hitbox (0.6 × 1.8, eye 1.62) while crouching, like 1.7.10
 *
 * <p>In 1.7.10 sneaking never changed the bounding box, so a gap lower than 1.8 blocks could not be
 * entered. 1.21.1 shrinks it to 1.5. Only client-side players are changed: the local player's movement
 * is computed by the client, so it can no longer walk into 1.5-high gaps on any server. The integrated
 * server (singleplayer) is never touched; the server side belongs to the Legacy Mechanics feature.</p>
 */
@Mixin(Player.class)
public abstract class OldSneakDimensionsMixin {

    @ModifyReturnValue(method = "getDefaultDimensions", at = @At("RETURN"))
    private EntityDimensions animatium$standingHitboxWhileCrouching(EntityDimensions dimensions, Pose pose) {
        Player self = (Player) (Object) this;
        if (pose == Pose.CROUCHING && self.level() != null && self.level().isClientSide()
            && AnimatiumFeature.getConfig().oldSneak) {
            return Player.STANDING_DIMENSIONS;
        }
        return dimensions;
    }
}

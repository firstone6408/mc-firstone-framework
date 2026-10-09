package io.github.firstone.framework.client.mixin.animatium.sneak;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.firstone.framework.features.animatium.OldSneak;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Places the camera at the 1.7.10 eye height, without the 1.21.1 eye-height smoothing
 *
 * <p>1.21.1 moves the camera eye height halfway to the target every tick ({@code Camera.tick}).
 * 1.7.10 had no such smoothing: the camera followed {@code posY}, interpolated per frame. This replaces
 * the single {@code Mth.lerp(FFF)F} eye-height call in {@code Camera.setup} with
 * {@code standing eye − interpolated 1.7.10 offset}, the same value used for aiming.</p>
 *
 * <p>When the vanilla value is far from it (for example right after leaving the swimming pose), the
 * vanilla value is kept until it catches up, so pose changes stay smooth.</p>
 */
@Mixin(Camera.class)
public class OldSneakCameraMixin {

    /** Maximum distance (blocks) between vanilla and legacy eye height for the legacy value to be used */
    private static final float MAX_BLEND_DISTANCE = 0.2F;

    @ModifyExpressionValue(
        method = "setup",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;lerp(FFF)F")
    )
    private float animatium$legacyCameraEyeHeight(float vanillaEyeHeight, BlockGetter level, Entity entity,
                                                  boolean detached, boolean mirrored, float partialTick) {
        if (entity == null || !OldSneak.appliesTo(entity)) {
            return vanillaEyeHeight;
        }
        float legacyEyeHeight = entity.getEyeHeight() + OldSneak.currentOffset() - OldSneak.offset(partialTick);
        return Math.abs(vanillaEyeHeight - legacyEyeHeight) <= MAX_BLEND_DISTANCE ? legacyEyeHeight : vanillaEyeHeight;
    }
}

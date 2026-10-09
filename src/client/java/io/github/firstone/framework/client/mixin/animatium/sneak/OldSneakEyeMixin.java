package io.github.firstone.framework.client.mixin.animatium.sneak;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.firstone.framework.features.animatium.OldSneak;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Applies the 1.7.10 sneak eye offset to the local player's eye height and eye position
 *
 * <p>{@code getEyeHeight()} uses the offset of the current tick (1.7.10 {@code posY}).
 * {@code getEyePosition(float)} is what {@code GameRenderer.pick} uses for the crosshair ray; it uses the
 * interpolated offset, exactly the value the camera uses ({@link OldSneakCameraMixin}), so the aim
 * always matches the view.</p>
 */
@Mixin(Entity.class)
public abstract class OldSneakEyeMixin {

    @ModifyReturnValue(method = "getEyeHeight()F", at = @At("RETURN"))
    private float animatium$legacyEyeHeight(float eyeHeight) {
        float offset = OldSneak.currentOffset();
        if (offset == 0.0F || !OldSneak.appliesTo((Entity) (Object) this)) {
            return eyeHeight;
        }
        return eyeHeight - offset;
    }

    @ModifyReturnValue(method = "getEyePosition(F)Lnet/minecraft/world/phys/Vec3;", at = @At("RETURN"))
    private Vec3 animatium$legacyEyePosition(Vec3 eyePosition, float partialTick) {
        if (!OldSneak.appliesTo((Entity) (Object) this)) {
            return eyePosition;
        }
        // getEyeHeight() already removed the current offset; swap it for the interpolated one
        float correction = OldSneak.currentOffset() - OldSneak.offset(partialTick);
        return correction == 0.0F ? eyePosition : eyePosition.add(0.0, correction, 0.0);
    }
}

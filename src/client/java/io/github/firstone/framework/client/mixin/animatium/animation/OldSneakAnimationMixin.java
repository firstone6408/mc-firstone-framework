package io.github.firstone.framework.client.mixin.animatium.animation;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that makes the sneak camera movement instant, like versions before 1.9
 *
 * <p>In newer versions the camera lowers gradually when Shift (sneak) is pressed,
 * because Camera lerps eyeHeight from the old value to the new one a little at a time</p>
 *
 * <p>This mixin forces {@code eyeHeight} and {@code eyeHeightOld}
 * to the target height immediately (the entity's current eye height, raised by 0.25 while crouching), so there is no transition</p>
 *
 * <p>Target: {@link Camera#setup} - the method that positions the camera every frame</p>
 */
@Mixin(Camera.class)
public class OldSneakAnimationMixin {

    /** Current eye height used for the lerp */
    @Shadow private float eyeHeight;

    /** Eye height of the previous frame, used with eyeHeight for interpolation */
    @Shadow private float eyeHeightOld;

    /**
     * Forces the camera's eyeHeight to the target height immediately
     *
     * <p>Injects at the start (HEAD) of setup(), before it reads these values, and overwrites eyeHeight and eyeHeightOld
     * with the target height to remove the lerp transition</p>
     *
     * @param level       world the camera is in
     * @param entity      entity the camera follows (usually the local player)
     * @param thirdPerson whether the camera is in third person
     * @param inverseView whether the camera view is inverted
     * @param partialTick interpolation value between ticks
     * @param ci          CallbackInfo of the injection
     */
    @Inject(method = "setup", at = @At("HEAD"))
    private void afterSetup(BlockGetter level, Entity entity, boolean thirdPerson,
                            boolean inverseView, float partialTick, CallbackInfo ci) {
        if (entity == null || !AnimatiumFeature.getConfig().oldSneakAnimation) {
            return;
        }

        // start from the vanilla eye height for the current pose
        float targetHeight = entity.getEyeHeight();

        // only adjust while sneaking
        if (entity.getPose() == Pose.CROUCHING) {
            // vanilla crouching eye height is about 1.27
            // raise it back by 0.25 blocks so the camera only drops slightly
            targetHeight += 0.25F;
        }

        // disable interpolation so the change is instant
        this.eyeHeight = targetHeight;
        this.eyeHeightOld = targetHeight;
    }
}
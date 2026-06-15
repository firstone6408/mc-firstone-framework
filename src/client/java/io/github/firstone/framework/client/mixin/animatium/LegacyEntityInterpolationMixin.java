package io.github.firstone.framework.client.mixin.animatium;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin that quantizes the partialTick of head/view rotation to the given FPS
 *
 * <p>Overrides {@link Entity#getViewXRot(float)} and {@link Entity#getViewYRot(float)}
 * to use a quantized partialTick → head turning and the camera move in steps
 * instead of lerping between frames</p>
 *
 * <p>{@code legacyHeadRotationFps = 0} → off (vanilla smooth)</p>
 *
 * <p>Target: {@link Entity}</p>
 */
@Mixin(Entity.class)
public class LegacyEntityInterpolationMixin {

    /** Current pitch (X rotation) */
    @Shadow private float xRot;

    /** Pitch of the previous tick */
    @Shadow public float xRotO;

    /** Current yaw (Y rotation) */
    @Shadow private float yRot;

    /** Yaw of the previous tick */
    @Shadow public float yRotO;

    /**
     * Quantizes partialTick to the given fps
     *
     * @param partialTick partialTick value (0.0–1.0)
     * @param fps         target FPS (must be greater than 0)
     * @return the quantized partialTick
     */
    @Unique
    private static float animatium$quantizeHead(float partialTick, int fps) {
        float stepsPerTick = fps / 20.0f;
        return (float) Math.floor(partialTick * stepsPerTick) / stepsPerTick;
    }

    /**
     * Overrides the pitch (X rotation) using the quantized partialTick
     *
     * <p>Original formula: {@code Mth.lerp(partialTick, xRotO, xRot)}</p>
     *
     * @param partialTick partialTick from the renderer
     * @param cir         CallbackInfo used to cancel and return the new value
     */
    @Inject(method = "getViewXRot(F)F", at = @At("HEAD"), cancellable = true)
    private void onGetViewXRot(float partialTick, CallbackInfoReturnable<Float> cir) {
        int fps = AnimatiumFeature.getConfig().legacyHeadRotationFps;
        if (fps <= 0) return;
        float q = animatium$quantizeHead(partialTick, fps);
        cir.setReturnValue(Mth.lerp(q, this.xRotO, this.xRot));
    }

    /**
     * Overrides the yaw (Y rotation) using the quantized partialTick
     *
     * <p>Original formula: {@code Mth.rotLerp(partialTick, yRotO, yRot)}</p>
     *
     * @param partialTick partialTick from the renderer
     * @param cir         CallbackInfo used to cancel and return the new value
     */
    @Inject(method = "getViewYRot(F)F", at = @At("HEAD"), cancellable = true)
    private void onGetViewYRot(float partialTick, CallbackInfoReturnable<Float> cir) {
        int fps = AnimatiumFeature.getConfig().legacyHeadRotationFps;
        if (fps <= 0) return;
        float q = animatium$quantizeHead(partialTick, fps);
        cir.setReturnValue(Mth.rotLerp(q, this.yRotO, this.yRot));
    }
}

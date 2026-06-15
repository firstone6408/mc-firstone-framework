package io.github.firstone.framework.client.mixin.animatium;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that snaps the head rotation (yHeadRot) of LivingEntity so it jumps every tick
 *
 * <p>Works together with {@link LegacyEntityInterpolationMixin}, which quantizes {@code getViewXRot/getViewYRot}
 * — snapping {@code yHeadRotO = yHeadRot} makes the entity's head model jump every tick
 * instead of lerping between frames</p>
 *
 * <p>Condition: {@code legacyHeadRotationFps > 0}</p>
 *
 * <p>Target: {@link LivingEntity#tick()}</p>
 */
@Mixin(LivingEntity.class)
public class LegacyLivingEntityInterpolationMixin {

    /** Current head yaw (used for model rendering) */
    @Shadow public float yHeadRot;

    /** Head yaw of the previous tick */
    @Shadow public float yHeadRotO;

    /**
     * Snaps yHeadRotO to yHeadRot after the tick finishes
     *
     * <p>Renderers that use {@code rotLerp(partialTick, yHeadRotO, yHeadRot)}
     * then get the same value on both ends → no lerp between frames</p>
     *
     * @param ci CallbackInfo of the injection
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void snapHeadRotationAfterTick(CallbackInfo ci) {
        if (AnimatiumFeature.getConfig().legacyHeadRotationFps > 0) {
            yHeadRotO = yHeadRot;
        }
    }
}

package io.github.firstone.framework.client.mixin.animatium;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin that limits the body rotation rate of entities so it moves in steps, like old versions
 *
 * <p>How it works: {@link LivingEntityRenderer#render} computes the body yaw with
 * {@code Mth.rotLerp(partialTick, yBodyRotO, yBodyRot)} reading the fields directly
 * (not through a method), so we {@code @Redirect} {@code Mth.rotLerp}
 * ordinal 0 in {@code render()} to quantize partialTick</p>
 *
 * <p>{@code legacyBodyRotationFps = 0} → off (everything goes through the original rotLerp)</p>
 *
 * <p>Target: {@link LivingEntityRenderer#render}</p>
 */
@Mixin(LivingEntityRenderer.class)
public class LegacyBodyRotationMixin {

    /**
     * Quantizes partialTick to the given fps
     *
     * @param partialTick partialTick value (0.0–1.0)
     * @param fps         target FPS (must be greater than 0)
     * @return the quantized partialTick
     */
    @Unique
    private static float animatium$quantize(float partialTick, int fps) {
        float stepsPerTick = fps / 20.0f;
        return (float) Math.floor(partialTick * stepsPerTick) / stepsPerTick;
    }

    /**
     * Redirects the body yaw calculation in render() to quantize partialTick
     *
     * <p>Ordinal 0 is {@code Mth.rotLerp(partialTick, yBodyRotO, yBodyRot)},
     * the first (body yaw) call (verified against the 1.21.1 bytecode)</p>
     *
     * @param delta partialTick passed to rotLerp (the partialTick of render())
     * @param from  yBodyRotO
     * @param to    yBodyRot
     * @return the body yaw quantized according to {@code legacyBodyRotationFps}
     */
    @Redirect(
        method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;rotLerp(FFF)F", ordinal = 0)
    )
    private float redirectBodyYawLerp(float delta, float from, float to) {
        int fps = AnimatiumFeature.getConfig().legacyBodyRotationFps;
        if (fps <= 0) return Mth.rotLerp(delta, from, to);
        return Mth.rotLerp(animatium$quantize(delta, fps), from, to);
    }
}

package io.github.firstone.framework.client.mixin.animatium;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin that limits the walk/limb animation rate so it moves in steps, like old versions
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>fps = 0</b>: off — vanilla values are used as usual</li>
 *   <li><b>fps 1–20</b>: Tick-Skip Mode — stores a frozen state
 *       and only updates it every {@code 20/fps} ticks
 *       to really "skip" ticks so the animation jumps instead of updating every tick</li>
 *   <li><b>fps 21–60</b>: Sub-Tick Quantize Mode — Quantize {@code partialTick}
 *       into {@code fps/20} steps per tick, for high render rates</li>
 * </ul>
 *
 * <p>Problem with the original formula ({@code floor(partialTick × stepsPerTick) / stepsPerTick}):
 * for fps ≤ 20 it always returns 0, but {@code position} and {@code speed}
 * still update every tick (20 times/second), so it looks no different from 20 fps</p>
 *
 * <p>Target: {@link WalkAnimationState}</p>
 */
@Mixin(WalkAnimationState.class)
public class LegacyWalkAnimationMixin {

    /** Animation cycle position, accumulated every tick */
    @Shadow private float position;

    /** Current animation speed */
    @Shadow private float speed;

    /** Animation speed of the previous tick */
    @Shadow private float speedOld;

    /** Frozen position value, only updated when the fps step comes around */
    @Unique private float animatium$frozenPosition = 0f;

    /** Frozen speed value, only updated when the fps step comes around */
    @Unique private float animatium$frozenSpeed = 0f;

    /** Number of ticks since the frozen state was last updated */
    @Unique private int animatium$tickCounter = 0;

    /**
     * Quantizes partialTick into steps, for Sub-Tick Mode (fps > 20)
     *
     * @param partialTick partialTick value (0.0–1.0)
     * @param fps         target FPS (must be greater than 20)
     * @return the quantized partialTick
     */
    @Unique
    private static float animatium$quantize(float partialTick, int fps) {
        float stepsPerTick = fps / 20.0f;
        return (float) Math.floor(partialTick * stepsPerTick) / stepsPerTick;
    }

    /**
     * Hooks into update(), which runs every tick, to manage the frozen state
     *
     * <p>For fps ≤ 20: counts ticks and only updates the frozen state every {@code 20/fps} ticks,
     * so the animation really skips ticks</p>
     *
     * @param ci CallbackInfo of the injection
     */
    @Inject(method = "update(FFF)V", at = @At("RETURN"))
    private void onUpdate(CallbackInfo ci) {
        int fps = AnimatiumFeature.getConfig().legacyWalkAnimationFps;
        if (fps <= 0 || fps > 20) return;

        int ticksPerStep = Math.max(1, 20 / fps);
        if (++animatium$tickCounter >= ticksPerStep) {
            animatium$tickCounter = 0;
            animatium$frozenPosition = this.position;
            animatium$frozenSpeed = this.speed;
        }
    }

    /**
     * Overrides the walk animation position according to the selected mode
     *
     * <p>fps ≤ 20 (Tick-Skip): returns {@code frozenPosition - frozenSpeed}</p>
     * <p>fps > 20 (Sub-Tick): returns the value quantized by partialTick</p>
     *
     * @param partialTick partialTick from the renderer
     * @param cir         CallbackInfo used to cancel and return the new value
     */
    @Inject(method = "position(F)F", at = @At("HEAD"), cancellable = true)
    private void onPosition(float partialTick, CallbackInfoReturnable<Float> cir) {
        int fps = AnimatiumFeature.getConfig().legacyWalkAnimationFps;
        if (fps <= 0) return;
        if (fps <= 20) {
            cir.setReturnValue(animatium$frozenPosition - animatium$frozenSpeed);
            return;
        }
        float q = animatium$quantize(partialTick, fps);
        cir.setReturnValue(this.position - this.speed * (1.0f - q));
    }

    /**
     * Overrides the walk animation speed according to the selected mode
     *
     * <p>fps ≤ 20 (Tick-Skip): returns {@code frozenSpeed}, constant for the whole frozen window</p>
     * <p>fps > 20 (Sub-Tick): returns the value quantized by partialTick</p>
     *
     * @param partialTick partialTick from the renderer
     * @param cir         CallbackInfo used to cancel and return the new value
     */
    @Inject(method = "speed(F)F", at = @At("HEAD"), cancellable = true)
    private void onSpeed(float partialTick, CallbackInfoReturnable<Float> cir) {
        int fps = AnimatiumFeature.getConfig().legacyWalkAnimationFps;
        if (fps <= 0) return;
        if (fps <= 20) {
            cir.setReturnValue(animatium$frozenSpeed);
            return;
        }
        float q = animatium$quantize(partialTick, fps);
        cir.setReturnValue(Mth.lerp(q, this.speedOld, this.speed));
    }
}

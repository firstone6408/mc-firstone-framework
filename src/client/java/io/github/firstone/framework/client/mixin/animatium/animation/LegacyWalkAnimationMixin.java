package io.github.firstone.framework.client.mixin.animatium.animation;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin that limits the walk/limb animation rate so it moves in steps, like old versions
 *
 * <h2>Modes</h2>
 * <ul>
 *   <li><b>fps = 0</b>: off — vanilla values are used as usual</li>
 *   <li><b>fps 1–20</b>: Tick-Skip Mode — keeps a frozen state
 *       and updates it when the game time reaches a new {@code 20/fps}-tick step,
 *       checked through {@link Minecraft#level} instead of injecting into {@code update()}
 *       to avoid mapping problems in production</li>
 *   <li><b>fps 21–60</b>: Sub-Tick Quantize Mode — Quantize {@code partialTick}
 *       into {@code fps/20} steps per tick</li>
 * </ul>
 *
 * <p>Target: {@link WalkAnimationState}</p>
 */
@Mixin(WalkAnimationState.class)
public class LegacyWalkAnimationMixin {

    @Shadow private float position;
    @Shadow private float speed;
    @Shadow private float speedOld;

    /** Frozen position value */
    @Unique private float animatium$frozenPosition = 0f;

    /** Frozen speed value */
    @Unique private float animatium$frozenSpeed = 0f;

    /** Last game-tick step at which the frozen state was updated */
    @Unique private long animatium$lastStep = -1L;

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
     * Updates the frozen state when a new fps step is reached
     *
     * <p>Uses {@link Minecraft#level#getGameTime()} instead of injecting into {@code update()}
     * because the method descriptor of {@code update} may be remapped incorrectly in production</p>
     *
     * @param fps Target FPS (1–20)
     */
    @Unique
    private void animatium$tryUpdateFrozen(int fps) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) return;

        long tick = mc.level.getGameTime();
        int ticksPerStep = Math.max(1, 20 / fps);
        long step = tick / ticksPerStep;

        if (step != animatium$lastStep) {
            animatium$lastStep = step;
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
            animatium$tryUpdateFrozen(fps);
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

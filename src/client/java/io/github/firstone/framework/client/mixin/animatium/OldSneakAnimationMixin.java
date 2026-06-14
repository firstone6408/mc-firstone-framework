package io.github.firstone.framework.client.mixin.animatium;

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
 * Mixin สำหรับทำให้ Sneak Animation เป็นแบบทันทีเหมือน version ก่อน 1.9
 *
 * <p>ใน version ใหม่ กล้องจะค่อยๆ เลื่อนลงเมื่อกด Shift (Sneak)
 * เพราะ Camera จะ Lerp ค่า eyeHeight จากค่าเดิมไปสู่ค่าใหม่ทีละนิด</p>
 *
 * <p>Mixin นี้บังคับให้ {@code eyeHeight} และ {@code eyeHeightOld}
 * มีค่าเท่ากับ eye height จริงของ Entity ทันที ทำให้ไม่มี Transition</p>
 *
 * <p>เป้าหมาย: {@link Camera#setup} - เมธอดที่ตั้งค่าตำแหน่งกล้องทุกเฟรม</p>
 */
@Mixin(Camera.class)
public class OldSneakAnimationMixin {

    /** ค่า eye height ปัจจุบันที่ใช้ Lerp */
    @Shadow private float eyeHeight;

    /** ค่า eye height เฟรมก่อนหน้า ใช้คู่กับ eyeHeight สำหรับ Interpolation */
    @Shadow private float eyeHeightOld;

    /**
     * บังคับให้ค่า eyeHeight ของกล้องตรงกับ eye height จริงของ Entity ทันที
     *
     * <p>Inject หลัง setup เสร็จ แล้วเขียนทับค่า eyeHeight และ eyeHeightOld
     * ให้เท่ากับ {@code entity.getEyeHeight()} เพื่อกำจัด Lerp Transition</p>
     *
     * @param level       World ที่กล้องอยู่
     * @param entity      Entity ที่กล้องติดตาม (ปกติคือ Local Player)
     * @param thirdPerson กล้องมุมที่สามหรือไม่
     * @param inverseView กล้องกลับด้านหรือไม่
     * @param partialTick ค่า Interpolation ระหว่าง Tick
     * @param ci          CallbackInfo ของ Inject
     */
    @Inject(method = "setup", at = @At("HEAD"))
    private void afterSetup(BlockGetter level, Entity entity, boolean thirdPerson,
                            boolean inverseView, float partialTick, CallbackInfo ci) {
        if (!AnimatiumFeature.getConfig().oldSneakAnimation) {
            return;
        }

        float targetHeight;

        if (entity.getPose() == Pose.CROUCHING) {
            // ยืนปกติแล้วลดลงเพียงเล็กน้อย
            targetHeight = entity.getEyeHeight(Pose.STANDING) - 0.10F;
        } else {
            // ยืนปกติ
            targetHeight = entity.getEyeHeight(Pose.STANDING);
        }

        this.eyeHeight = targetHeight;
        this.eyeHeightOld = targetHeight;
    }
}
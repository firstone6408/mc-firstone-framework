package io.github.firstone.framework.client.mixin.animatium;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin สำหรับลบท่าทาง Animation การโยนออกเมื่อทิ้ง Item เหมือน version ก่อน 1.9
 *
 * <p>ใน version ใหม่ เมื่อกด Q เพื่อทิ้ง Item จะมี Animation แกว่งแขนขึ้นมา
 * Mixin นี้ใช้ Flag เพื่อตรวจสอบว่ากำลัง drop Item อยู่หรือไม่
 * และยกเลิก swing Animation ในช่วงเวลานั้น</p>
 *
 * <p>เป้าหมาย: {@link LocalPlayer#drop(boolean)} และ {@link LocalPlayer#swing(InteractionHand)}</p>
 */
@Mixin(LocalPlayer.class)
public class OldItemDropAnimationMixin {

    /**
     * Flag บ่งบอกว่ากำลังอยู่ในการ drop Item
     *
     * <p>ถูกตั้งเป็น true ก่อน drop() และ reset เป็น false ในช่วงต้นของ tick ถัดไป
     * เพื่อให้ swing() ที่ถูกเรียกใน call stack เดียวกับ drop() ถูกยกเลิก</p>
     */
    @Unique
    private boolean animatium$droppingItem = false;

    @Unique
    private boolean animatium$droppingLastItem = false;

    /**
     * ตั้ง Flag ก่อนทิ้ง Item เพื่อให้ swing() รู้ว่ากำลัง drop อยู่
     *
     * @param fullStack  true = ทิ้งทั้ง Stack, false = ทิ้งชิ้นเดียว
     * @param cir        CallbackInfo สำหรับเมธอดที่คืนค่า boolean
     */
    @Inject(method = "drop(Z)Z", at = @At("HEAD"))
    private void beforeDrop(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        if (!AnimatiumFeature.getConfig().oldItemDropAnimation) {
            return;
        }

        LocalPlayer player = (LocalPlayer)(Object)this;

        animatium$droppingItem = true;
        animatium$droppingLastItem =
                player.getMainHandItem().getCount() == 1;
    }

    /**
     * Reset Flag ในต้น tick เพื่อไม่ให้ส่งผลต่อ swing() ใน tick ถัดไป
     *
     * <p>เรียกทุก tick เพื่อ cleanup Flag หากไม่มี swing() ถูกเรียก</p>
     *
     * @param ci CallbackInfo ของ tick
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        animatium$droppingItem = false;
    }

    /**
     * ยกเลิก swing Animation หากกำลัง drop Item อยู่
     *
     * <p>ตรวจสอบ Flag {@code animatium$droppingItem} และ Cancel swing
     * เพื่อป้องกันท่าทางโยน Item ใน version ใหม่</p>
     *
     * @param hand มือที่จะแกว่ง
     * @param ci   CallbackInfo ที่สามารถ Cancel ได้
     */
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"), cancellable = true)
    private void onSwing(InteractionHand hand, CallbackInfo ci) {
        if (animatium$droppingItem && !animatium$droppingLastItem) {
            ci.cancel();
        }
    }
}

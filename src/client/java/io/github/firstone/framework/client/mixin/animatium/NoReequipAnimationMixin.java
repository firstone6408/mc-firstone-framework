package io.github.firstone.framework.client.mixin.animatium;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin สำหรับลบ Attack Cooldown animation ออกเหมือน version ก่อน 1.9
 *
 * <p>แบ่ง animation ออกเป็น 3 phase:</p>
 * <ul>
 *   <li><b>DOWN phase</b> ({@code switchInProgress}): vanilla ควบคุมทั้งหมด — item เดิมลงที่ความเร็ว vanilla</li>
 *   <li><b>RISE phase</b> ({@code riseActive}): เราขับ {@code mainHandHeight} ด้วย timer เอง
 *       เพื่อให้ rise ไม่ถูกขัดจาก attack cooldown (สาเหตุหลักที่ animation อุปกรณ์ถูก cancel)</li>
 *   <li><b>IDLE phase</b>: บังคับ height = 1.0f เพื่อลบ attack cooldown animation</li>
 * </ul>
 *
 * <p>root cause ของ equipment bug: หลัง vanilla switch item แล้ว player โจมตีทันที
 * {@code attackStrengthScale} ลดลง ทำให้ vanilla ไม่ยก height ขึ้น → rise timer ({@code riseTicks})
 * หมดก่อน rise เสร็จ → เราบังคับ 1.0f → เห็นเป็น animation ถูก cancel
 * การขับ rise ด้วย timer ของเราเองแก้ปัญหานี้ได้</p>
 *
 * <p>เป้าหมาย: {@link ItemInHandRenderer#tick()}</p>
 */
@Mixin(ItemInHandRenderer.class)
public class NoReequipAnimationMixin {

    /** อ้างอิง Minecraft instance เพื่อเข้าถึง player ปัจจุบัน */
    @Shadow private Minecraft minecraft;

    /** Item ที่ renderer กำลัง render (อาจต่างจาก player's item ระหว่าง animation) */
    @Shadow private ItemStack mainHandItem;

    /** ค่า height ปัจจุบันของมือหลัก */
    @Shadow private float mainHandHeight;

    /** ค่า height ของมือหลักในเฟรมก่อนหน้า ใช้สำหรับ interpolation */
    @Shadow private float oMainHandHeight;

    /** ค่า height ของมือรอง */
    @Shadow private float offHandHeight;

    /** ค่า height ของมือรองในเฟรมก่อนหน้า */
    @Shadow private float oOffHandHeight;

    /** Item ที่ renderer กำลัง render ก่อน tick ทำงาน ใช้ตรวจจับ justSwitched */
    @Unique private ItemStack animatium$prevMainHandItem = ItemStack.EMPTY;

    /** true = เรากำลังขับ rise animation อยู่ (ไม่ใช่ vanilla) */
    @Unique private boolean animatium$riseActive = false;

    /** timer สำหรับ rise phase (0.0 → 1.0 ที่ 0.4f/tick = 3 tick เหมือน vanilla) */
    @Unique private float animatium$riseTimer = 0.0f;

    /**
     * บันทึก Item ที่กำลัง render ก่อน tick ทำงาน
     *
     * @param ci CallbackInfo ของ Inject
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void beforeTick(CallbackInfo ci) {
        if (!AnimatiumFeature.getConfig().noReequipAnimation) return;
        animatium$prevMainHandItem = this.mainHandItem;
    }

    /**
     * จัดการ animation หลัง vanilla tick ทำงานเสร็จ
     *
     * <p>logic หลัก:</p>
     * <ol>
     *   <li>DOWN phase ({@code switchInProgress}): vanilla ควบคุม — รีเซ็ต rise ถ้ามี switch ใหม่</li>
     *   <li>RISE start ({@code justSwitched}): vanilla เพิ่งสลับ item ที่ height 0 → เริ่ม timer ของเรา</li>
     *   <li>RISE ({@code riseActive}): ขับ {@code mainHandHeight} ด้วย timer ของเรา (ไม่ขึ้นกับ cooldown)</li>
     *   <li>IDLE: บังคับ height = 1.0f เพื่อลบ attack cooldown animation</li>
     * </ol>
     *
     * @param ci CallbackInfo ของ Inject
     */
    @Inject(method = "tick", at = @At("RETURN"))
    private void afterTick(CallbackInfo ci) {
        if (!AnimatiumFeature.getConfig().noReequipAnimation) {
            animatium$riseActive = false;
            return;
        }

        LocalPlayer player = this.minecraft.player;
        if (player == null) return;

        ItemStack currentItem = player.getMainHandItem();

        // สนใจแค่ชนิดของ item ไม่สน durability
        boolean itemsMatch = ItemStack.isSameItem(this.mainHandItem, currentItem);

        // vanilla กำลังทำ dip ลง
        boolean switchInProgress = !itemsMatch;

        // เพิ่งเปลี่ยน item จริง ๆ
        boolean justSwitched =
                !ItemStack.isSameItem(animatium$prevMainHandItem, currentItem)
                && itemsMatch;

        if (switchInProgress) {
            animatium$riseActive = false;

        } else if (justSwitched) {
            animatium$riseActive = true;
            animatium$riseTimer = 0.0f;
            mainHandHeight = 0.0f;

        } else if (animatium$riseActive) {
            animatium$riseTimer = Math.min(1.0f, animatium$riseTimer + 0.4f);
            mainHandHeight = animatium$riseTimer;

            if (animatium$riseTimer >= 1.0f) {
                animatium$riseActive = false;
            }

        } else {
            // ไม่มี animation อะไรเลย
            animatium$riseActive = false;
            animatium$riseTimer = 1.0f;

            mainHandHeight = 1.0f;
            oMainHandHeight = 1.0f;
        }

        offHandHeight = 1.0f;
        oOffHandHeight = 1.0f;
    }
}

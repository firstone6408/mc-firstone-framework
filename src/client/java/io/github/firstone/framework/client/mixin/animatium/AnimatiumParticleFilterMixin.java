package io.github.firstone.framework.client.mixin.animatium;

import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin สำหรับกรอง Particle ฝั่ง Client ที่ไม่ต้องการแสดงผล
 *
 * <p>Particle เช่น {@code SWEEP_ATTACK} และ {@code DAMAGE_INDICATOR} ถูกส่งมาจาก Server
 * ทั้งใน Singleplayer (ผ่าน Integrated Server) และ Multiplayer (ผ่านเครือข่าย)
 * โดยเข้ามาที่ {@link ClientLevel#addParticle(ParticleOptions, boolean, double, double, double, double, double, double)}</p>
 *
 * <p>Mixin นี้สกัดกั้นที่จุดนี้เพื่อยกเลิก Particle ที่ไม่ต้องการ
 * ก่อนที่มันจะถูกส่งต่อไปยัง {@code LevelRenderer}</p>
 *
 * <p>เป้าหมาย: {@link ClientLevel#addParticle(ParticleOptions, boolean, double, double, double, double, double, double)}</p>
 */
@Mixin(ClientLevel.class)
public class AnimatiumParticleFilterMixin {

    /**
     * ยกเลิก Particle ที่ถูกส่งจาก Server หากการตั้งค่าปิดการแสดงผลไว้
     *
     * <p>ตรวจสอบประเภท Particle และเปรียบเทียบกับ config:</p>
     * <ul>
     *   <li>{@code noSweepEffect} — ซ่อน {@code SWEEP_ATTACK} Particle</li>
     *   <li>{@code noDamageIndicatorParticle} — ซ่อน {@code DAMAGE_INDICATOR} Particle</li>
     * </ul>
     *
     * @param particle    ข้อมูล Particle ที่จะแสดง
     * @param forceSpawn  บังคับให้แสดง Particle แม้ไกลจากผู้เล่น
     * @param x           ตำแหน่ง X
     * @param y           ตำแหน่ง Y
     * @param z           ตำแหน่ง Z
     * @param dx          ความเร็วแกน X
     * @param dy          ความเร็วแกน Y
     * @param dz          ความเร็วแกน Z
     * @param ci          CallbackInfo ที่สามารถ Cancel ได้
     */
    @Inject(
        method = "addParticle(Lnet/minecraft/core/particles/ParticleOptions;ZDDDDDD)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onAddParticle(ParticleOptions particle, boolean forceSpawn,
                                double x, double y, double z,
                                double dx, double dy, double dz,
                                CallbackInfo ci) {
        AnimatiumConfig config = AnimatiumFeature.getConfig();
        if (config.noSweepEffect && particle == ParticleTypes.SWEEP_ATTACK) {
            ci.cancel();
            return;
        }
        if (config.noDamageIndicatorParticle && particle == ParticleTypes.DAMAGE_INDICATOR) {
            ci.cancel();
        }
    }
}

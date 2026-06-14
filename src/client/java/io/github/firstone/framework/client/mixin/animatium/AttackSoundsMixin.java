package io.github.firstone.framework.client.mixin.animatium;

import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mixin สำหรับซ่อนเสียงโจมตีและเสียง Sweep เหมือน version ก่อน 1.9
 *
 * <p>ใน version ใหม่ การโจมตีจะมีเสียงหลากหลายประเภท เช่น CRIT, STRONG, WEAK, NODAMAGE
 * และเสียง SWEEP_ATTACK สำหรับการโจมตีพัด ซึ่งไม่มีใน version เก่าก่อน 1.9</p>
 *
 * <p>Mixin นี้ใช้ {@code @Redirect} เพื่อสกัดกั้น {@code Level.playSound()} ทุกสายใน
 * {@code Player.attack()} และตัดสินใจว่าจะเล่นเสียงหรือไม่ตามการตั้งค่า</p>
 *
 * <p>เป้าหมาย: {@link Player#attack(Entity)}</p>
 */
@Mixin(Player.class)
public class AttackSoundsMixin {

    /**
     * Redirect การเรียก {@code Level.playSound} แบบ 6 พารามิเตอร์ใน {@code attack()}
     *
     * <p>ใช้สำหรับ {@code PLAYER_ATTACK_NODAMAGE} (การกด 1 ครั้งแรก ไม่มี Volume/Pitch)</p>
     *
     * @param level   Level ที่จะเล่นเสียง
     * @param player  ผู้เล่นที่เป็นต้นกำเนิด (อาจเป็น null)
     * @param x       ตำแหน่ง X
     * @param y       ตำแหน่ง Y
     * @param z       ตำแหน่ง Z
     * @param sound   SoundEvent ที่จะเล่น
     * @param source  SoundSource ของเสียง
     */
    @Redirect(
        method = "attack(Lnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;)V")
    )
    private void redirectAttackSoundNoVolume(Level level, Player player,
                                              double x, double y, double z,
                                              SoundEvent sound, SoundSource source) {
        if (!AnimatiumFeature.getConfig().noAttackSounds) {
            level.playSound(player, x, y, z, sound, source);
        }
    }

    /**
     * Redirect การเรียก {@code Level.playSound} แบบ 8 พารามิเตอร์ใน {@code attack()}
     *
     * <p>ใช้สำหรับ {@code PLAYER_ATTACK_KNOCKBACK}, {@code PLAYER_ATTACK_SWEEP},
     * {@code PLAYER_ATTACK_CRIT}, {@code PLAYER_ATTACK_STRONG}, {@code PLAYER_ATTACK_WEAK}
     * และ {@code PLAYER_ATTACK_NODAMAGE} (ครั้งที่ 2 ที่มี Volume/Pitch)</p>
     *
     * <p>ตรวจสอบ config {@code noAttackSounds} เพื่อบล็อกทุกเสียง
     * และ {@code noSweepEffect} เพื่อบล็อกเฉพาะเสียง Sweep</p>
     *
     * @param level   Level ที่จะเล่นเสียง
     * @param player  ผู้เล่นที่เป็นต้นกำเนิด (อาจเป็น null)
     * @param x       ตำแหน่ง X
     * @param y       ตำแหน่ง Y
     * @param z       ตำแหน่ง Z
     * @param sound   SoundEvent ที่จะเล่น
     * @param source  SoundSource ของเสียง
     * @param volume  ความดังเสียง
     * @param pitch   ระดับเสียง
     */
    @Redirect(
        method = "attack(Lnet/minecraft/world/entity/Entity;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;playSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V")
    )
    private void redirectAttackSound(Level level, Player player,
                                      double x, double y, double z,
                                      SoundEvent sound, SoundSource source,
                                      float volume, float pitch) {
        AnimatiumConfig config = AnimatiumFeature.getConfig();
        if (config.noAttackSounds) return;
        if (config.noSweepEffect && sound == SoundEvents.PLAYER_ATTACK_SWEEP) return;
        level.playSound(player, x, y, z, sound, source, volume, pitch);
    }
}

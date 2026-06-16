package io.github.firstone.framework.client.mixin.animatium.combat;

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
 * Mixin that hides attack sounds and the sweep sound, like versions before 1.9
 *
 * <p>In newer versions attacks play many kinds of sounds, such as CRIT, STRONG, WEAK, NODAMAGE,
 * plus the SWEEP_ATTACK sound for sweep attacks, none of which existed before 1.9</p>
 *
 * <p>This mixin uses {@code @Redirect} to intercept every {@code Level.playSound()} call in
 * {@code Player.attack()} and decides whether to play the sound based on the settings</p>
 *
 * <p>Target: {@link Player#attack(Entity)}</p>
 */
@Mixin(Player.class)
public class AttackSoundsMixin {

    /**
     * Redirects the 6-parameter {@code Level.playSound} call in {@code attack()}
     *
     * <p>Used for {@code PLAYER_ATTACK_NODAMAGE} (the first call site, without volume/pitch)</p>
     *
     * @param level   level to play the sound in
     * @param player  player the sound originates from (may be null)
     * @param x       X position
     * @param y       Y position
     * @param z       Z position
     * @param sound   SoundEvent to play
     * @param source  SoundSource of the sound
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
     * Redirects the 8-parameter {@code Level.playSound} call in {@code attack()}
     *
     * <p>Used for {@code PLAYER_ATTACK_KNOCKBACK}, {@code PLAYER_ATTACK_SWEEP},
     * {@code PLAYER_ATTACK_CRIT}, {@code PLAYER_ATTACK_STRONG}, {@code PLAYER_ATTACK_WEAK}
     * and {@code PLAYER_ATTACK_NODAMAGE} (the second call site, with volume/pitch)</p>
     *
     * <p>Checks the {@code noAttackSounds} config to block every sound
     * and {@code noSweepEffect} to block only the sweep sound</p>
     *
     * @param level   level to play the sound in
     * @param player  player the sound originates from (may be null)
     * @param x       X position
     * @param y       Y position
     * @param z       Z position
     * @param sound   SoundEvent to play
     * @param source  SoundSource of the sound
     * @param volume  sound volume
     * @param pitch   sound pitch
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

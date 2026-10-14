package io.github.firstone.framework.client.mixin.animatium.combat;

import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/**
 * Mutes the player attack sounds added in 1.9 ({@code entity.player.attack.*}); 1.7.10 had none of them
 *
 * <p>The server plays these sounds and sends them to every client, so muting them inside
 * {@code Player.attack} only worked in singleplayer. Filtering in {@link SoundEngine#play}, where every sound
 * the client plays ends up (including delayed ones), works in singleplayer and on any server.</p>
 *
 * <ul>
 *   <li>{@code noAttackSounds}: crit, knockback, nodamage, strong, weak and sweep</li>
 *   <li>{@code noSweepEffect}: the sweep sound only</li>
 * </ul>
 */
@Mixin(SoundEngine.class)
public class LegacyCombatSoundsMixin {

    /** Every attack sound added in 1.9 */
    @Unique
    private static final Set<ResourceLocation> ANIMATIUM$ATTACK_SOUNDS = Set.of(
        SoundEvents.PLAYER_ATTACK_CRIT.getLocation(),
        SoundEvents.PLAYER_ATTACK_KNOCKBACK.getLocation(),
        SoundEvents.PLAYER_ATTACK_NODAMAGE.getLocation(),
        SoundEvents.PLAYER_ATTACK_STRONG.getLocation(),
        SoundEvents.PLAYER_ATTACK_SWEEP.getLocation(),
        SoundEvents.PLAYER_ATTACK_WEAK.getLocation()
    );

    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void animatium$filterAttackSound(SoundInstance sound, CallbackInfo ci) {
        AnimatiumConfig config = AnimatiumFeature.getConfig();
        ResourceLocation id = sound.getLocation();
        if ((config.noAttackSounds && ANIMATIUM$ATTACK_SOUNDS.contains(id))
            || (config.noSweepEffect && id.equals(SoundEvents.PLAYER_ATTACK_SWEEP.getLocation()))) {
            ci.cancel();
        }
    }
}

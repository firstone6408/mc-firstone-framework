package io.github.firstone.framework.client.mixin.cosmetics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.firstone.framework.features.cosmetics.CosmeticEffects;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.JukeboxSong;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Plays the song file chosen for a music disc ({@link CosmeticEffects#musicDisc})
 *
 * <p>Only the sound is swapped: the jukebox, the "Now Playing" text and the song length stay the game's, so the
 * song stops when the disc's time is over or the disc is taken out.</p>
 */
@Mixin(LevelRenderer.class)
public class CosmeticMusicDiscMixin {

    @WrapOperation(method = "playJukeboxSong", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/resources/sounds/SimpleSoundInstance;forJukeboxSong(Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/client/resources/sounds/SimpleSoundInstance;"))
    private SimpleSoundInstance cosmetics$musicDisc(SoundEvent sound, Vec3 pos, Operation<SimpleSoundInstance> original,
                                                    @Local(argsOnly = true) Holder<JukeboxSong> song) {
        SimpleSoundInstance custom = CosmeticEffects.musicDisc(song, pos);
        return custom != null ? custom : original.call(sound, pos);
    }
}

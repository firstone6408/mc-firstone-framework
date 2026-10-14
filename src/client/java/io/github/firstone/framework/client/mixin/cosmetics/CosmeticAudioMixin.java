package io.github.firstone.framework.client.mixin.cosmetics;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.audio.SoundBuffer;
import io.github.firstone.framework.features.cosmetics.CosmeticAudio;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import javax.sound.sampled.AudioFormat;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Lets {@link CosmeticAudio} process a Cosmetics sound while it loads (mono 3D sound, skipped start)
 *
 * <p>The game loads sounds in two lambdas of {@code SoundBufferLibrary}, known by their intermediary names (the same
 * in development and production): {@code method_19747} decodes a short sound at once ({@code getCompleteBuffer}),
 * {@code method_19745} opens a song to read while it plays ({@code getStream}). Both open the file from the list of
 * files found at the last resource reload, so a variant name is first turned into its file's name
 * ({@link CosmeticAudio#file}). Sounds that are not Cosmetics variants pass through unchanged.</p>
 */
@Mixin(SoundBufferLibrary.class)
public class CosmeticAudioMixin {

    @WrapOperation(method = {"method_19747", "method_19745"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/server/packs/resources/ResourceProvider;open(Lnet/minecraft/resources/ResourceLocation;)Ljava/io/InputStream;"))
    private InputStream cosmetics$openFile(ResourceProvider provider, ResourceLocation location,
                                           Operation<InputStream> original) {
        return original.call(provider, CosmeticAudio.file(location));
    }

    @WrapOperation(method = "method_19747", at = @At(value = "NEW",
        target = "(Ljava/nio/ByteBuffer;Ljavax/sound/sampled/AudioFormat;)Lcom/mojang/blaze3d/audio/SoundBuffer;"))
    private SoundBuffer cosmetics$processBuffer(ByteBuffer samples, AudioFormat format, Operation<SoundBuffer> original,
                                                @Local(argsOnly = true) ResourceLocation location) {
        return CosmeticAudio.buffer(location, samples, format, original::call);
    }

    @ModifyReturnValue(method = "method_19745", at = @At("RETURN"))
    private AudioStream cosmetics$processStream(AudioStream stream, @Local(argsOnly = true) ResourceLocation location) {
        return CosmeticAudio.stream(location, stream);
    }
}

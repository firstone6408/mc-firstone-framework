package io.github.firstone.framework.devtest.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

/**
 * Reads a loaded sound file as the game will play it; part of the dev test mod only
 */
@Mixin(SoundBuffer.class)
public interface SoundBufferAccessor {

    /**
     * Returns the sample format (channels: 1 is mono, placed in the world; 2 is stereo)
     *
     * @return {@code format}
     */
    @Accessor("format")
    AudioFormat devtest$getFormat();

    /**
     * Returns the samples, until the buffer is first played (then they move to the sound card)
     *
     * @return {@code data}, or null once played
     */
    @Accessor("data")
    @Nullable
    ByteBuffer devtest$getData();
}

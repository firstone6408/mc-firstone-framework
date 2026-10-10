package io.github.firstone.framework.devtest.mixin;

import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the library of loaded sound files; part of the dev test mod only
 */
@Mixin(SoundEngine.class)
public interface SoundEngineAccessor {

    /**
     * Returns the library that loads and keeps sound files
     *
     * @return {@code soundBuffers}
     */
    @Accessor("soundBuffers")
    SoundBufferLibrary devtest$getSoundBuffers();
}

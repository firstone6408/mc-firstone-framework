package io.github.firstone.framework.devtest.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reaches the sound engine behind the sound manager; part of the dev test mod only
 */
@Mixin(SoundManager.class)
public interface SoundManagerAccessor {

    /**
     * Returns the sound engine
     *
     * @return {@code soundEngine}
     */
    @Accessor("soundEngine")
    SoundEngine devtest$getSoundEngine();
}

package io.github.firstone.framework.devtest.mixin;

import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Makes a fish bite a fishing hook, so a test can reel in a catch at once; part of the dev test mod only
 */
@Mixin(FishingHook.class)
public interface FishingHookAccessor {

    /**
     * Sets how many more ticks a fish bites (more than 0: reeling in now catches something)
     *
     * @param ticks the ticks
     */
    @Accessor("nibble")
    void devtest$setNibble(int ticks);
}

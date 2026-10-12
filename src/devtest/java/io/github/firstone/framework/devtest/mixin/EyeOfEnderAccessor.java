package io.github.firstone.framework.devtest.mixin;

import net.minecraft.world.entity.projectile.EyeOfEnder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Ends an eye of ender's flight at once, so a test can see the item it drops; part of the dev test mod only
 */
@Mixin(EyeOfEnder.class)
public interface EyeOfEnderAccessor {

    /**
     * Sets how many ticks the eye has flown (its flight ends after 80)
     *
     * @param ticks the ticks
     */
    @Accessor("life")
    void devtest$setLife(int ticks);

    /**
     * Sets whether the eye drops as an item at the end of its flight (else it breaks)
     *
     * @param survives true to drop the item
     */
    @Accessor("surviveAfterDeath")
    void devtest$setSurviveAfterDeath(boolean survives);
}

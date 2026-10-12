package io.github.firstone.framework.devtest.mixin;

import net.minecraft.world.entity.ExperienceOrb;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads how many orbs an experience orb stands for, so a test can see merged orbs; part of the dev test mod only
 */
@Mixin(ExperienceOrb.class)
public interface ExperienceOrbAccessor {

    /**
     * Returns how many orbs this one stands for (more than 1 after orbs merged into it)
     *
     * @return {@code count}
     */
    @Accessor("count")
    int devtest$getCount();
}

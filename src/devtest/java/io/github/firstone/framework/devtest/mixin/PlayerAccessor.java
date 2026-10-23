package io.github.firstone.framework.devtest.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Sets the player's enchanting seed, so a test can make 1.21.1 roll with a chosen seed; part of the dev test mod only
 */
@Mixin(Player.class)
public interface PlayerAccessor {

    /**
     * Sets the seed 1.21.1 rolls the enchanting table with ({@code enchantmentSeed}, saved as {@code XpSeed})
     *
     * @param seed the seed
     */
    @Accessor("enchantmentSeed")
    void devtest$setEnchantmentSeed(int seed);
}

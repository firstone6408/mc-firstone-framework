package io.github.firstone.framework.devtest.mixin;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the first-person hand animation (how far the held item is raised); part of the dev test mod only
 */
@Mixin(ItemInHandRenderer.class)
public interface HandRendererAccessor {

    /**
     * Returns how far the main hand item is raised: 1 when held up, 0 when fully lowered (re-equip)
     *
     * @return {@code mainHandHeight}
     */
    @Accessor("mainHandHeight")
    float devtest$getMainHandHeight();

    /**
     * Returns the main hand item the renderer draws (it may still be the old item while lowering)
     *
     * @return {@code mainHandItem}
     */
    @Accessor("mainHandItem")
    ItemStack devtest$getMainHandItem();

    /**
     * Returns how far the off hand item is raised: 1 when held up, 0 when fully lowered
     *
     * @return {@code offHandHeight}
     */
    @Accessor("offHandHeight")
    float devtest$getOffHandHeight();
}

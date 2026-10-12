package io.github.firstone.framework.client.mixin.animatium.item;

import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.renderer.entity.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Gives access to the item colors the item renderer tints items with (dyed leather, potions, spawn eggs…)
 *
 * <p>Used by {@code LegacyGroundItemMixin}, which draws the flat pictures of Fast graphics itself.</p>
 */
@Mixin(ItemRenderer.class)
public interface ItemRendererAccessor {

    /**
     * Returns the item colors the item renderer uses
     *
     * @return {@code itemColors}
     */
    @Accessor("itemColors")
    ItemColors animatium$getItemColors();
}

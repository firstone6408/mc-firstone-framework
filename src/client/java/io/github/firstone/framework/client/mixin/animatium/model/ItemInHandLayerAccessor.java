package io.github.firstone.framework.client.mixin.animatium.model;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the hand renderer of the held item layer, which draws the item ({@code LegacyThirdPersonItem})
 */
@Mixin(ItemInHandLayer.class)
public interface ItemInHandLayerAccessor {

    /**
     * Returns the hand renderer the layer draws items with
     *
     * @return {@code itemInHandRenderer}
     */
    @Accessor("itemInHandRenderer")
    ItemInHandRenderer animatium$getItemInHandRenderer();
}

package io.github.firstone.framework.client.mixin.animatium.reequip;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Gives access to the main-hand stack the first-person hand renderer is showing
 *
 * <p>Used by {@code OldItemDropReequipMixin} to start the re-equip animation after a drop.</p>
 */
@Mixin(ItemInHandRenderer.class)
public interface ItemInHandRendererAccessor {

    /**
     * Returns the main-hand stack currently rendered in first person
     *
     * @return the rendered main-hand stack
     */
    @Accessor("mainHandItem")
    ItemStack getMainHandItem();

    /**
     * Replaces the main-hand stack currently rendered in first person
     *
     * @param stack the stack to render
     */
    @Accessor("mainHandItem")
    void setMainHandItem(ItemStack stack);
}

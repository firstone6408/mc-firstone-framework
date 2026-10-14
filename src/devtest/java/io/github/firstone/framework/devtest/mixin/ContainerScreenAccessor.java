package io.github.firstone.framework.devtest.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads where a container screen (inventory, chest...) is drawn; part of the dev test mod only
 */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {

    /**
     * Returns the x position of the screen's panel
     *
     * @return {@code leftPos}, in GUI pixels
     */
    @Accessor("leftPos")
    int devtest$getLeftPos();

    /**
     * Returns the width of the screen's panel
     *
     * @return {@code imageWidth}, in GUI pixels
     */
    @Accessor("imageWidth")
    int devtest$getImageWidth();
}

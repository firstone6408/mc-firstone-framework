package io.github.firstone.framework.devtest.mixin;

import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads when the loading overlay finished loading; part of the dev test mod only
 */
@Mixin(LoadingOverlay.class)
public interface LoadingOverlayAccessor {

    /**
     * Returns when loading was done
     *
     * @return {@code fadeOutStart} in milliseconds ({@code Util.getMillis}), or -1 while loading
     */
    @Accessor("fadeOutStart")
    long devtest$getFadeOutStart();
}

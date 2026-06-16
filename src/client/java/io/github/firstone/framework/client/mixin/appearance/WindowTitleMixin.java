package io.github.firstone.framework.client.mixin.appearance;

import com.mojang.blaze3d.platform.Window;
import io.github.firstone.framework.features.appearance.AppearanceFeature;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that intercepts {@link Window#setTitle(String)} every time Minecraft tries to set the window title
 *
 * <p>Instead of waiting for a tick event (which makes the title flicker), this mixin intercepts every call
 * and applies the custom title immediately, so the title does not flicker when changing screens or opening containers</p>
 *
 * <p>If {@code windowTitle} is empty, Minecraft's own title is used as normal</p>
 */
@Mixin(Window.class)
public class WindowTitleMixin {

    /**
     * Intercepts setTitle and replaces it with the custom title, if one is set
     *
     * @param title the title Minecraft is trying to set
     * @param ci    CallbackInfo used to cancel the original call
     */
    @Inject(method = "setTitle", at = @At("HEAD"), cancellable = true)
    private void onSetTitle(String title, CallbackInfo ci) {
        String custom = AppearanceFeature.getConfig().windowTitle;
        if (!custom.isEmpty()) {
            GLFW.glfwSetWindowTitle(((Window) (Object) this).getWindow(), custom);
            ci.cancel();
        }
    }
}

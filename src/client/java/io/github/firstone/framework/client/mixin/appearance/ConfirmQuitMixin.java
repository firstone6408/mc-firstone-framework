package io.github.firstone.framework.client.mixin.appearance;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.Window;
import io.github.firstone.framework.features.appearance.QuitConfirmation;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets "Confirm Quit" take over close requests of the game window
 *
 * <p>Every tick {@code Minecraft.runTick} checks {@code window.shouldClose()} (set when the window's close button is
 * pressed) and calls {@code stop()} if it is true. This mixin asks {@link QuitConfirmation#interceptClose} first; when
 * it takes the request over, the check reports false and the game keeps running while the dialog is shown.</p>
 */
@Mixin(Minecraft.class)
public class ConfirmQuitMixin {

    @WrapOperation(
        method = "runTick",
        at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;shouldClose()Z")
    )
    private boolean appearance$confirmQuit(Window window, Operation<Boolean> original) {
        return original.call(window) && !QuitConfirmation.interceptClose((Minecraft) (Object) this);
    }
}

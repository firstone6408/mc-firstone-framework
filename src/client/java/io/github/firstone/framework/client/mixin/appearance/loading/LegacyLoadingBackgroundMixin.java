package io.github.firstone.framework.client.mixin.appearance.loading;

import io.github.firstone.framework.features.appearance.loading.LegacyLoading;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Legacy Loading Screens: the 1.7.10 dirt background behind the loading screens ({@link LegacyLoading})
 *
 * <p>{@code Screen.renderBackground} draws the background of the screens that keep it (the world loading screen, the
 * connecting screen, the progress screen); the message screen ("Saving world", "Reading world data...") and the
 * screen shown while a world or dimension is received draw their own, so they are changed too. Every other screen
 * keeps its 1.21.1 background.</p>
 */
@Mixin({Screen.class, GenericMessageScreen.class, ReceivingLevelScreen.class})
public class LegacyLoadingBackgroundMixin {

    @Inject(method = "renderBackground", at = @At("HEAD"), cancellable = true)
    private void appearance$dirtBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                           CallbackInfo ci) {
        Screen screen = (Screen) (Object) this;
        if (LegacyLoading.dirtBackground(screen)) {
            LegacyLoading.renderDirt(graphics, screen.width, screen.height);
            ci.cancel();
        }
    }
}

package io.github.firstone.framework.client.mixin.appearance.loading;

import io.github.firstone.framework.features.appearance.loading.LegacyLoading;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Legacy Loading Screens: "Downloading terrain" (1.7.10 {@code GuiDownloadTerrain}) in place of "Loading terrain..."
 * while a world or dimension is received ({@link LegacyLoading}); the text stays where both versions put it
 */
@Mixin(ReceivingLevelScreen.class)
public class LegacyReceivingLevelMixin {

    @ModifyArg(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"),
        index = 1)
    private Component appearance$downloadingTerrain(Component text) {
        return LegacyLoading.receivingText(text);
    }
}

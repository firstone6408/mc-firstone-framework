package io.github.firstone.framework.client.mixin.appearance.loading;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.appearance.loading.LegacyLoading;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.progress.StoringChunkProgressListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Legacy Loading Screens: "Loading world" and "Building terrain" (and the 1.7.10 progress bar if it is on) in place of
 * the chunk map and the percentage while a world is made ({@link LegacyLoading})
 *
 * <p>The screen still follows the world's progress and narrates it as in 1.21.1; only the drawing changes.</p>
 */
@Mixin(LevelLoadingScreen.class)
public abstract class LegacyLevelLoadingMixin extends Screen {

    @Shadow @Final private StoringChunkProgressListener progressListener;

    private LegacyLevelLoadingMixin(Component title) {
        super(title);
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;renderChunks(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/server/level/progress/StoringChunkProgressListener;IIII)V"))
    private void appearance$noChunkMap(GuiGraphics graphics, StoringChunkProgressListener listener, int x, int y,
                                       int size, int gap, Operation<Void> original) {
        if (!LegacyLoading.legacyWorldLoading()) {
            original.call(graphics, listener, x, y, size, gap);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;drawCenteredString(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private void appearance$noPercentage(GuiGraphics graphics, Font font, Component text, int x, int y, int color,
                                         Operation<Void> original) {
        if (!LegacyLoading.legacyWorldLoading()) {
            original.call(graphics, font, text, x, y, color);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void appearance$legacyTexts(GuiGraphics graphics, int mouseX, int mouseY, float partialTick,
                                        CallbackInfo ci) {
        if (LegacyLoading.legacyWorldLoading()) {
            LegacyLoading.renderWorldLoading(graphics, this.font, this.width, this.height,
                this.progressListener.getProgress());
        }
    }
}

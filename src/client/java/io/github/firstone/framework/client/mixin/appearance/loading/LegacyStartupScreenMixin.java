package io.github.firstone.framework.client.mixin.appearance.loading;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import io.github.firstone.framework.features.appearance.loading.LegacyLoading;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Legacy Startup Screen: the 1.7.10 startup screen while the game loads its resources ({@link LegacyLoading})
 *
 * <p>Only the drawing calls of {@code LoadingOverlay.render} are changed: the red background becomes white (or
 * black), the logo
 * is drawn in red (or a logo from the logos folder), the 1.21.1 progress bar is left out (the 1.7.10 bar takes its
 * place if Startup Bar is on), and nothing fades. The overlay
 * itself still tracks the loading, reports when it is done and closes when 1.21.1 closes it; from the moment 1.21.1
 * starts fading it out, the screen behind shows at once.</p>
 */
@Mixin(LoadingOverlay.class)
public class LegacyStartupScreenMixin {

    @Shadow private long fadeOutStart;
    @Shadow private float currentProgress;

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/platform/GlStateManager;_clearColor(FFFF)V"))
    private void appearance$whiteStartup(float red, float green, float blue, float alpha, Operation<Void> original) {
        if (LegacyLoading.legacyStartup()) {
            int color = LegacyLoading.startupBackground();
            original.call((color >> 16 & 0xFF) / 255.0F, (color >> 8 & 0xFF) / 255.0F, (color & 0xFF) / 255.0F, 1.0F);
        } else {
            original.call(red, green, blue, alpha);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V"))
    private void appearance$whiteOrNothing(GuiGraphics graphics, RenderType type, int left, int top, int right,
                                           int bottom, int color, Operation<Void> original) {
        if (!LegacyLoading.legacyStartup()) {
            original.call(graphics, type, left, top, right, bottom, color);
        } else if (!LegacyLoading.startupFinished(this.fadeOutStart)) {
            original.call(graphics, type, left, top, right, bottom, LegacyLoading.startupBackground());
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lcom/mojang/blaze3d/systems/RenderSystem;blendFunc(II)V"))
    private void appearance$normalBlend(int source, int destination, Operation<Void> original) {
        if (LegacyLoading.legacyStartup()) {
            RenderSystem.defaultBlendFunc();
        } else {
            original.call(source, destination);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;setColor(FFFF)V", ordinal = 0))
    private void appearance$logoColor(GuiGraphics graphics, float red, float green, float blue, float alpha,
                                      Operation<Void> original) {
        if (LegacyLoading.legacyStartup()) {
            LegacyLoading.setLogoColor(graphics, !LegacyLoading.startupFinished(this.fadeOutStart));
        } else {
            original.call(graphics, red, green, blue, alpha);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V",
        ordinal = 0))
    private void appearance$logoLeft(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width,
                                     int height, float u, float v, int uWidth, int vHeight, int textureWidth,
                                     int textureHeight, Operation<Void> original) {
        if (!LegacyLoading.legacyStartup() || !LegacyLoading.drawCustomLogo(graphics)) {
            original.call(graphics, texture, x, y, width, height, u, v, uWidth, vHeight, textureWidth, textureHeight);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIIIFFIIII)V",
        ordinal = 1))
    private void appearance$logoRight(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width,
                                      int height, float u, float v, int uWidth, int vHeight, int textureWidth,
                                      int textureHeight, Operation<Void> original) {
        // a logo from the logos folder was drawn whole with the left half
        if (!LegacyLoading.legacyStartup() || !LegacyLoading.hasCustomLogo()) {
            original.call(graphics, texture, x, y, width, height, u, v, uWidth, vHeight, textureWidth, textureHeight);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;drawProgressBar(Lnet/minecraft/client/gui/GuiGraphics;IIIIF)V"))
    private void appearance$noProgressBar(LoadingOverlay overlay, GuiGraphics graphics, int left, int top, int right,
                                          int bottom, float alpha, Operation<Void> original) {
        if (!LegacyLoading.legacyStartup()) {
            original.call(overlay, graphics, left, top, right, bottom, alpha);
        } else {
            LegacyLoading.renderStartupBar(graphics, this.currentProgress);
        }
    }
}

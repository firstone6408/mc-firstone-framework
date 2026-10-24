package io.github.firstone.framework.client.mixin.appearance.loading;

import io.github.firstone.framework.features.appearance.loading.LegacyLoading;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.FocusableTextWidget;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Legacy Message Screens: the message screens ("Reading world data...", "Saving world") without their box, with the
 * 1.7.10 text where 1.7.10 wrote it ({@link LegacyLoading#renderMessage})
 *
 * <p>The box is the screen's own text widget, hidden; the screen still opens and closes as in 1.21.1 and its title is
 * narrated as before.</p>
 */
@Mixin(GenericMessageScreen.class)
public abstract class LegacyMessageScreenMixin extends Screen {

    @Shadow @Nullable private FocusableTextWidget textWidget;

    private LegacyMessageScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void appearance$hideBox(CallbackInfo ci) {
        if (this.textWidget != null && LegacyLoading.legacyMessages()) {
            this.textWidget.visible = false;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (this.textWidget != null && !this.textWidget.visible) {
            LegacyLoading.renderMessage(graphics, this.font, this.title, this.width, this.height);
        }
    }
}

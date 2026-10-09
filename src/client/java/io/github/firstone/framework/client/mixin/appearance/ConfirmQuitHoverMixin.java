package io.github.firstone.framework.client.mixin.appearance;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.appearance.QuitConfirmation;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hides the mouse from the screen below the "Quit the game?" dialog
 *
 * <p>While the dialog is open, the screen is drawn with a mouse position outside the window, so nothing behind the
 * dialog is highlighted and no item or button tooltip appears there. Clicks are blocked separately by
 * {@link QuitConfirmation}.</p>
 */
@Mixin(GameRenderer.class)
public class ConfirmQuitHoverMixin {

    @WrapOperation(
        method = "render",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;renderWithTooltip(Lnet/minecraft/client/gui/GuiGraphics;IIF)V")
    )
    private void appearance$hideMouseBelowDialog(Screen screen, GuiGraphics graphics, int mouseX, int mouseY,
                                                 float partialTick, Operation<Void> original) {
        boolean hidden = QuitConfirmation.isOpen();
        original.call(screen, graphics, hidden ? -1 : mouseX, hidden ? -1 : mouseY, partialTick);
    }
}

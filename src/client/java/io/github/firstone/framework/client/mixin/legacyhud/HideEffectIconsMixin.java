package io.github.firstone.framework.client.mixin.legacyhud;

import io.github.firstone.framework.features.legacyhud.LegacyHudFeature;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the status effect icons in the top-right corner of the HUD ({@code Gui.renderEffects}), like 1.7.10
 *
 * <p>1.7.10 {@code GuiIngame} drew no effect icons; active effects were only listed in the inventory
 * ({@code InventoryEffectRenderer}). Only the HUD layer is skipped: the effects, their screen effects
 * (nausea, darkness…) and the effect list of the inventory screen are not changed.</p>
 */
@Mixin(Gui.class)
public class HideEffectIconsMixin {

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void legacyHud$hideEffectIcons(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (LegacyHudFeature.getConfig().hideEffectIcons) {
            ci.cancel();
        }
    }
}

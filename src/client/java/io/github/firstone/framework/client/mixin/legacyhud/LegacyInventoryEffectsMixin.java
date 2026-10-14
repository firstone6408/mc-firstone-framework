package io.github.firstone.framework.client.mixin.legacyhud;

import io.github.firstone.framework.features.legacyhud.LegacyInventoryEffects;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Draws the inventory effect list on the left of the window ({@link LegacyInventoryEffects}) while the screen uses
 * the 1.7.10 layout, instead of the 1.21.1 list on the right
 *
 * <p>The layout is chosen by {@code LegacyInventoryPositionMixin} (inventory) and
 * {@code LegacyCreativePositionMixin} (creative inventory) when the screen opens.</p>
 */
@Mixin(EffectRenderingInventoryScreen.class)
public abstract class LegacyInventoryEffectsMixin<T extends AbstractContainerMenu> extends AbstractContainerScreen<T>
    implements LegacyInventoryEffects.Layout {

    /** True while this screen uses the 1.7.10 layout */
    @Unique
    private boolean legacyHud$effectsOnLeft;

    private LegacyInventoryEffectsMixin(T menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public boolean legacyHud$effectsOnLeft() {
        return this.legacyHud$effectsOnLeft;
    }

    @Override
    public void legacyHud$setEffectsOnLeft(boolean onLeft) {
        this.legacyHud$effectsOnLeft = onLeft;
    }

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void legacyHud$renderEffectsOnLeft(GuiGraphics guiGraphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (this.legacyHud$effectsOnLeft) {
            LegacyInventoryEffects.render(guiGraphics, this.minecraft, this.font, this.leftPos, this.topPos);
            ci.cancel();
        }
    }

    /** The list on the left is visible, so the HUD hides its effect icons, as it does for the 1.21.1 list */
    @Inject(method = "canSeeEffects", at = @At("HEAD"), cancellable = true)
    private void legacyHud$effectsVisible(CallbackInfoReturnable<Boolean> cir) {
        if (this.legacyHud$effectsOnLeft) {
            cir.setReturnValue(true);
        }
    }
}

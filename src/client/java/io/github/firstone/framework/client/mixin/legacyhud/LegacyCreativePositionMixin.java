package io.github.firstone.framework.client.mixin.legacyhud;

import io.github.firstone.framework.features.legacyhud.LegacyInventoryEffects;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Moves the creative inventory to the 1.7.10 position when it opens while the player has effects
 * ({@link LegacyInventoryEffects})
 *
 * <p>Runs at the end of {@code AbstractContainerScreen.init}, which the creative inventory calls before it places
 * its search box and tabs, so everything follows the new position. Other screens are not changed.</p>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class LegacyCreativePositionMixin extends Screen {

    @Shadow
    protected int leftPos;

    @Shadow
    protected int imageWidth;

    private LegacyCreativePositionMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void legacyHud$moveCreativeInventory(CallbackInfo ci) {
        if ((Object) this instanceof CreativeModeInventoryScreen screen) {
            boolean onLeft = LegacyInventoryEffects.shouldMoveLeft(this.minecraft);
            ((LegacyInventoryEffects.Layout) screen).legacyHud$setEffectsOnLeft(onLeft);
            if (onLeft) {
                this.leftPos = LegacyInventoryEffects.leftPos(this.width, this.imageWidth);
            }
        }
    }
}

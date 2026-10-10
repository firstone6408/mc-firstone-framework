package io.github.firstone.framework.client.mixin.legacyhud;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.legacyhud.LegacyInventoryEffects;
import net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Moves the inventory to the 1.7.10 position when it opens while the player has effects and the recipe book is
 * closed ({@link LegacyInventoryEffects})
 *
 * <p>The inventory gets its position from {@code RecipeBookComponent.updateScreenPosition} when it opens
 * ({@code init}) and when the recipe book button is pressed ({@code method_19891}, the button's lambda; its
 * intermediary name is the same in development and production). The recipe book button follows the window.
 * While the recipe book is open, the 1.21.1 position and effect list are kept.</p>
 */
@Mixin(InventoryScreen.class)
public abstract class LegacyInventoryPositionMixin extends EffectRenderingInventoryScreen<InventoryMenu> {

    private LegacyInventoryPositionMixin(InventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @WrapOperation(method = {"init", "method_19891"}, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screens/recipebook/RecipeBookComponent;updateScreenPosition(II)I"))
    private int legacyHud$moveInventory(RecipeBookComponent recipeBook, int width, int imageWidth,
                                        Operation<Integer> original) {
        boolean onLeft = !recipeBook.isVisible() && LegacyInventoryEffects.shouldMoveLeft(this.minecraft);
        ((LegacyInventoryEffects.Layout) this).legacyHud$setEffectsOnLeft(onLeft);
        return onLeft
            ? LegacyInventoryEffects.leftPos(width, imageWidth)
            : original.call(recipeBook, width, imageWidth);
    }
}

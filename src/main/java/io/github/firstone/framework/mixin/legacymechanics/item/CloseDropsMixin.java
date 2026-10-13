package io.github.firstone.framework.mixin.legacymechanics.item;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.legacymechanics.item.CloseDrops;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Throws the items a closing screen gives back, instead of putting them into the inventory ({@link CloseDrops})
 *
 * <p>Every place where 1.21.1 gives items back when a screen closes calls {@code Inventory.placeItemBackInInventory}:
 * {@code AbstractContainerMenu.removed} for the cursor item, {@code AbstractContainerMenu.clearContainer} for the
 * slots of the crafting grids, the enchanting table, the anvil and the other work blocks, and
 * {@code MerchantMenu.removed} for a villager's two payment slots. Only that call is replaced; the branch where
 * 1.21.1 already throws the items (a dead or disconnected player) is not touched. Runs on the server (also the
 * integrated one).</p>
 */
@Mixin({AbstractContainerMenu.class, MerchantMenu.class})
public class CloseDropsMixin {

    @WrapOperation(
        method = {"removed", "clearContainer"},
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Inventory;placeItemBackInInventory(Lnet/minecraft/world/item/ItemStack;)V"))
    private void legacymechanics$dropOnClose(Inventory inventory, ItemStack stack, Operation<Void> original) {
        if (!CloseDrops.dropInstead(inventory, stack)) {
            original.call(inventory, stack);
        }
    }
}

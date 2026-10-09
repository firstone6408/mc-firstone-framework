package io.github.firstone.framework.client.mixin.animatium.drop;

import io.github.firstone.framework.client.mixin.animatium.reequip.ItemInHandRendererAccessor;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Plays the 1.7.10 lower-then-raise animation when dropping empties the hand
 *
 * <p>In 1.7.10 dropping only sent a packet; the server then emptied the slot and the hand renderer saw a
 * new (empty) stack, so the item went down and the empty arm came up. In 1.21.1 {@code LocalPlayer.drop}
 * shrinks the held stack to 0 in place: the renderer still holds the same object, so the item just
 * vanishes. Before the stack is shrunk, this mixin keeps a copy and hands it to the renderer, which then
 * runs its normal re-equip animation from that copy to the empty hand.</p>
 *
 * <p>Only applies when the dropped stack becomes empty (last item, or Ctrl+Q) and the renderer is showing
 * that stack; dropping one item of a bigger stack plays nothing, as in 1.7.10.</p>
 */
@Mixin(LocalPlayer.class)
public class OldItemDropReequipMixin {

    /** Copy of the stack that is about to be emptied by this drop, or EMPTY */
    @Unique
    private ItemStack animatium$droppedStack = ItemStack.EMPTY;

    @Inject(method = "drop(Z)Z", at = @At("HEAD"))
    private void animatium$rememberDroppedStack(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        this.animatium$droppedStack = ItemStack.EMPTY;
        if (!AnimatiumFeature.getConfig().oldItemDrop) {
            return;
        }
        ItemStack selected = ((LocalPlayer) (Object) this).getInventory().getSelected();
        boolean emptiesHand = !selected.isEmpty() && (fullStack || selected.getCount() == 1);
        if (emptiesHand && renderer().getMainHandItem() == selected) {
            this.animatium$droppedStack = selected.copy();
        }
    }

    @Inject(method = "drop(Z)Z", at = @At("RETURN"))
    private void animatium$reequipFromDroppedStack(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        if (!this.animatium$droppedStack.isEmpty() && cir.getReturnValueZ()) {
            renderer().setMainHandItem(this.animatium$droppedStack);
        }
        this.animatium$droppedStack = ItemStack.EMPTY;
    }

    @Unique
    private static ItemInHandRendererAccessor renderer() {
        return (ItemInHandRendererAccessor) Minecraft.getInstance().gameRenderer.itemInHandRenderer;
    }
}

package io.github.firstone.framework.client.mixin.animatium.animation;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin that removes the throwing gesture when dropping an item, like versions before 1.9
 *
 * <p>In newer versions, pressing Q to drop an item swings the arm.
 * This mixin uses a flag to know whether an item is being dropped
 * and cancels the swing animation during that time, unless the main-hand stack had only one item</p>
 *
 * <p>Targets: {@link LocalPlayer#drop(boolean)} and {@link LocalPlayer#swing(InteractionHand)}</p>
 */
@Mixin(LocalPlayer.class)
public class OldItemDropAnimationMixin {

    /**
     * Flag telling whether an item is currently being dropped
     *
     * <p>Set to true before drop() and reset to false at the start of the next tick,
     * so the swing() that vanilla calls right after drop() in the same tick is cancelled</p>
     */
    @Unique
    private boolean animatium$droppingItem = false;

    @Unique
    private boolean animatium$droppingLastItem = false;

    /**
     * Sets the flag before dropping an item so swing() knows a drop is in progress
     *
     * @param fullStack  true = drop the whole stack, false = drop a single item
     * @param cir        CallbackInfo of a method returning boolean
     */
    @Inject(method = "drop(Z)Z", at = @At("HEAD"))
    private void beforeDrop(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        if (!AnimatiumFeature.getConfig().oldItemDropAnimation) {
            return;
        }

        LocalPlayer player = (LocalPlayer)(Object)this;

        animatium$droppingItem = true;
        animatium$droppingLastItem =
                player.getMainHandItem().getCount() == 1;
    }

    /**
     * Resets the flag at the start of each tick so it does not affect swing() in later ticks
     *
     * <p>Runs every tick to clear the flag even if no swing() was called</p>
     *
     * @param ci CallbackInfo of tick
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        animatium$droppingItem = false;
    }

    /**
     * Cancels the swing animation while an item is being dropped
     *
     * <p>Checks the {@code animatium$droppingItem} flag and cancels the swing
     * to prevent the throwing gesture of newer versions; the swing is kept when the
     * main-hand stack had only one item (animatium$droppingLastItem)</p>
     *
     * @param hand the hand that would swing
     * @param ci   cancellable CallbackInfo
     */
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"), cancellable = true)
    private void onSwing(InteractionHand hand, CallbackInfo ci) {
        if (animatium$droppingItem && !animatium$droppingLastItem) {
            ci.cancel();
        }
    }
}

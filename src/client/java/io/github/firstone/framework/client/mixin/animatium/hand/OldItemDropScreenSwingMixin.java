package io.github.firstone.framework.client.mixin.animatium.hand;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/**
 * Removes the arm swing after throwing an item out of a screen, like 1.7.10
 *
 * <p>Items thrown out of an open screen (clicked outside the window, Ctrl+Q over a slot, thrown out of the creative
 * inventory) go through {@code Player.drop(ItemStack, boolean, boolean)}, which in 1.21.1 swings the arm when it
 * runs on the client. 1.7.10 {@code EntityPlayer.func_146097_a} never swung. This mixin skips only that call; the
 * item is still thrown, and the server, which never swings there, is not changed. The drop key's swing is removed
 * by {@link OldItemDropSwingMixin}.</p>
 */
@Mixin(Player.class)
public class OldItemDropScreenSwingMixin {

    @WrapWithCondition(
        method = "drop(Lnet/minecraft/world/item/ItemStack;ZZ)Lnet/minecraft/world/entity/item/ItemEntity;",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;swing(Lnet/minecraft/world/InteractionHand;)V")
    )
    private boolean animatium$noSwingOnScreenDrop(Player player, InteractionHand hand) {
        return !AnimatiumFeature.getConfig().oldItemDrop;
    }
}

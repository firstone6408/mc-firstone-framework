package io.github.firstone.framework.client.mixin.animatium.drop;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;

import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Removes the arm swing after dropping an item, like 1.7.10
 *
 * <p>1.7.10 {@code Minecraft.runTick} only called {@code dropOneItem}; it never swung the arm.
 * 1.21.1 {@code Minecraft.handleKeybinds} calls {@code player.swing(MAIN_HAND)} after a successful drop —
 * the only {@code swing} call in that method — and this mixin skips exactly that call, so no other swing
 * (attacking, mining, placing) is affected.</p>
 */
@Mixin(Minecraft.class)
public class OldItemDropSwingMixin {

    @WrapWithCondition(
        method = "handleKeybinds",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V")
    )
    private boolean animatium$noSwingOnDrop(LocalPlayer player, InteractionHand hand) {
        return !AnimatiumFeature.getConfig().oldItemDrop;
    }
}

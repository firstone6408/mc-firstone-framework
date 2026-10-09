package io.github.firstone.framework.client.mixin.animatium.use;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 1.7.10 right-click animations: swing only on blocks, dip only when the used stack changed
 *
 * <p>1.7.10 {@code Minecraft.rightClickMouse}: right-clicking a block swings the arm; right-clicking an
 * entity or using an item in the air (ender pearl, egg, snowball, potion…) never swings. After using an item
 * in the air, the hand re-equips only if {@code sendUseItem} reports a changed stack (count changed or a
 * different stack, e.g. a thrown pearl); starting to eat, draw a bow or block does not.</p>
 *
 * <p>1.21.1 {@code Minecraft.startUseItem} swings for entities (ordinal 0), blocks (ordinal 1) and items in the
 * air (ordinal 2), and calls {@code itemUsed} after every successful use in the air (ordinal 1). This mixin
 * skips swings 0 and 2 and applies the 1.7.10 condition to {@code itemUsed} ordinal 1. The block branch is
 * already identical to 1.7.10 and is left alone.</p>
 */
@Mixin(Minecraft.class)
public class OldItemUseMixin {

    /** Stack held before the last item use in the air */
    @Unique
    private ItemStack animatium$stackBeforeUse = ItemStack.EMPTY;

    /** Count of that stack before the use */
    @Unique
    private int animatium$countBeforeUse;

    @WrapWithCondition(
        method = "startUseItem",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V", ordinal = 0)
    )
    private boolean animatium$noSwingOnEntityUse(LocalPlayer player, InteractionHand hand) {
        return !AnimatiumFeature.getConfig().oldItemUse;
    }

    @WrapWithCondition(
        method = "startUseItem",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;)V", ordinal = 2)
    )
    private boolean animatium$noSwingOnItemUse(LocalPlayer player, InteractionHand hand) {
        return !AnimatiumFeature.getConfig().oldItemUse;
    }

    @WrapOperation(
        method = "startUseItem",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;")
    )
    private InteractionResult animatium$rememberStackBeforeUse(MultiPlayerGameMode gameMode, Player player, InteractionHand hand,
                                                              Operation<InteractionResult> original) {
        ItemStack held = player.getItemInHand(hand);
        this.animatium$stackBeforeUse = held;
        this.animatium$countBeforeUse = held.getCount();
        InteractionResult result = original.call(gameMode, player, hand);
        if (!result.consumesAction()) {
            // itemUsed() will not be called; do not keep a reference to the stack
            this.animatium$stackBeforeUse = ItemStack.EMPTY;
        }
        return result;
    }

    @WrapWithCondition(
        method = "startUseItem",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;itemUsed(Lnet/minecraft/world/InteractionHand;)V", ordinal = 1)
    )
    private boolean animatium$dipOnlyWhenStackChanged(ItemInHandRenderer renderer, InteractionHand hand) {
        if (!AnimatiumFeature.getConfig().oldItemUse) {
            return true;
        }
        LocalPlayer player = ((Minecraft) (Object) this).player;
        ItemStack after = player == null ? ItemStack.EMPTY : player.getItemInHand(hand);
        boolean changed = after != this.animatium$stackBeforeUse || after.getCount() != this.animatium$countBeforeUse;
        this.animatium$stackBeforeUse = ItemStack.EMPTY;
        return changed;
    }
}

package io.github.firstone.framework.client.mixin.animatium.reequip;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import io.github.firstone.framework.features.animatium.LegacyReequip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the 1.7.10 re-equip rules to the main hand in first person
 *
 * <p>1.7.10 {@code ItemRenderer.updateEquippedItem} raised the hand fully whenever the rendered item could
 * stay, and had no attack cooldown. Two small changes to vanilla {@code ItemInHandRenderer.tick} give the
 * same result while keeping its animation speed (±0.4 per tick) and swap point (below 0.1):</p>
 * <ul>
 *   <li>the main-hand {@code ItemStack.matches} check (ordinal 0) also accepts stacks allowed by
 *       {@link LegacyReequip#keepsItem}, so they replace the rendered stack without animation;</li>
 *   <li>the attack strength used for the hand height is forced to 1, so attacking never dips the hand.</li>
 * </ul>
 *
 * <p>{@code itemUsed()} (placing a block, throwing a pearl…) is left alone: 1.7.10 also reset the hand
 * there. The off hand did not exist in 1.7.10 and keeps the vanilla behavior.</p>
 */
@Mixin(ItemInHandRenderer.class)
public class LegacyReequipMixin {

    @Shadow
    private ItemStack mainHandItem;

    @Shadow
    @Final
    private Minecraft minecraft;

    /** Hotbar slot the rendered main-hand stack came from (-1 = unknown) */
    @Unique
    private int animatium$renderedSlot = -1;

    @WrapOperation(
        method = "tick",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;matches(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemStack;)Z",
            ordinal = 0)
    )
    private boolean animatium$legacyMainHandMatch(ItemStack rendered, ItemStack current, Operation<Boolean> original) {
        boolean vanillaMatch = original.call(rendered, current);
        if (vanillaMatch || !AnimatiumFeature.getConfig().legacyReequip || this.minecraft.player == null) {
            return vanillaMatch;
        }
        boolean slotChanged = this.minecraft.player.getInventory().selected != this.animatium$renderedSlot;
        return LegacyReequip.keepsItem(rendered, current, slotChanged);
    }

    @ModifyExpressionValue(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAttackStrengthScale(F)F")
    )
    private float animatium$noAttackCooldownDip(float attackStrength) {
        return AnimatiumFeature.getConfig().legacyReequip ? 1.0F : attackStrength;
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void animatium$trackRenderedSlot(CallbackInfo ci) {
        if (this.minecraft.player != null && this.mainHandItem == this.minecraft.player.getMainHandItem()) {
            this.animatium$renderedSlot = this.minecraft.player.getInventory().selected;
        }
    }
}

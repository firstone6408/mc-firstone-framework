package io.github.firstone.framework.client.mixin.animatium.hand;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.firstone.framework.features.animatium.hand.LegacyFirstPersonItem;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws held items in first person with the 1.7.10 placement ({@link LegacyFirstPersonItem})
 *
 * <p>Runs before {@code renderArmWithItem} draws one hand; when the 1.7.10 placement applies, the item is drawn there
 * and the 1.21.1 code for that hand is left out. Empty hands, maps, crossbows and the other items it leaves alone keep
 * the 1.21.1 code.</p>
 */
@Mixin(ItemInHandRenderer.class)
public class LegacyFirstPersonItemMixin {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void animatium$legacyFirstPersonItem(AbstractClientPlayer player, float partialTick, float pitch,
                                                 InteractionHand hand, float swing, ItemStack stack, float equip,
                                                 PoseStack poseStack, MultiBufferSource buffers, int light,
                                                 CallbackInfo ci) {
        if (LegacyFirstPersonItem.render((ItemInHandRenderer) (Object) this, player, partialTick, hand, swing, stack,
            equip, poseStack, buffers, light)) {
            ci.cancel();
        }
    }
}

package io.github.firstone.framework.client.mixin.animatium.model;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.firstone.framework.features.animatium.model.LegacyThirdPersonItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws held items in third person with the 1.7.10 placement ({@link LegacyThirdPersonItem})
 *
 * <p>Runs before {@code renderArmWithItem} draws one arm's item (players reach it through
 * {@code PlayerItemInHandLayer}, except for a spyglass in use); when the 1.7.10 placement applies, the item is drawn
 * there and the 1.21.1 code is left out.</p>
 */
@Mixin(ItemInHandLayer.class)
public class LegacyThirdPersonItemMixin {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void animatium$legacyThirdPersonItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                                 HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffers,
                                                 int light, CallbackInfo ci) {
        if (LegacyThirdPersonItem.render(((RenderLayer<?, ?>) (Object) this).getParentModel(),
            ((ItemInHandLayerAccessor) this).animatium$getItemInHandRenderer(), entity, stack, context, arm, poseStack,
            buffers, light)) {
            ci.cancel();
        }
    }
}

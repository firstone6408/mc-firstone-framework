package io.github.firstone.framework.client.mixin.animatium.item;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.firstone.framework.features.animatium.item.LegacyGroundItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws items lying on the ground like 1.7.10 ({@link LegacyGroundItem})
 *
 * <p>At the start of {@code ItemEntityRenderer.render}: when the item is drawn the 1.7.10 way, the rest of the
 * method is left out, and {@code EntityRenderer.render} is called as the method itself does at its end, so the item's
 * name is still drawn. Extends {@code EntityRenderer} only to make that call.</p>
 */
@Mixin(ItemEntityRenderer.class)
public abstract class LegacyGroundItemMixin extends EntityRenderer<ItemEntity> {

    @Shadow
    @Final
    private ItemRenderer itemRenderer;

    private LegacyGroundItemMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Inject(method = "render(Lnet/minecraft/world/entity/item/ItemEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;"
        + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At("HEAD"), cancellable = true)
    private void animatium$legacyGroundItem(ItemEntity entity, float yaw, float partialTick, PoseStack poseStack,
                                            MultiBufferSource buffers, int light, CallbackInfo ci) {
        if (LegacyGroundItem.render(entity, partialTick, poseStack, buffers, light, this.itemRenderer,
            ((ItemRendererAccessor) this.itemRenderer).animatium$getItemColors(),
            this.entityRenderDispatcher.camera.getYRot())) {
            super.render(entity, yaw, partialTick, poseStack, buffers, light);
            ci.cancel();
        }
    }
}

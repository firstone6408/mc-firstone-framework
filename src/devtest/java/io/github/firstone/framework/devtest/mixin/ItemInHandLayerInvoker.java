package io.github.firstone.framework.devtest.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Draws one held item of an entity on its own, so a test can capture where it is drawn; part of the dev test mod
 * only
 */
@Mixin(ItemInHandLayer.class)
public interface ItemInHandLayerInvoker {

    /**
     * Draws the item in one arm, as the layer does for each arm every frame
     *
     * @param entity    the entity
     * @param stack     the item
     * @param context   third person, left or right hand
     * @param arm       the arm
     * @param poseStack the pose stack, at the model
     * @param buffers   the buffers to draw into
     * @param light     packed light
     */
    @Invoker("renderArmWithItem")
    void devtest$renderArmWithItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context, HumanoidArm arm,
                                   PoseStack poseStack, MultiBufferSource buffers, int light);
}

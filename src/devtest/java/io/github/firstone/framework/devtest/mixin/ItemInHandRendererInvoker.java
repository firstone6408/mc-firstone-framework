package io.github.firstone.framework.devtest.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Draws one first-person hand on its own, so a test can capture where the held item is drawn; part of the dev test
 * mod only
 */
@Mixin(ItemInHandRenderer.class)
public interface ItemInHandRendererInvoker {

    /**
     * Draws a hand with its item, as the game does for each hand every frame
     *
     * @param player      the player
     * @param partialTick progress between ticks
     * @param pitch       the camera pitch (used by maps only)
     * @param hand        the hand
     * @param swing       swing progress (0 when not swinging)
     * @param stack       the item in the hand
     * @param equip       how far the item is lowered (0 held up, 1 fully down)
     * @param poseStack   the pose stack to draw with
     * @param buffers     the buffers to draw into
     * @param light       packed light
     */
    @Invoker("renderArmWithItem")
    void devtest$renderArmWithItem(AbstractClientPlayer player, float partialTick, float pitch, InteractionHand hand,
                                   float swing, ItemStack stack, float equip, PoseStack poseStack,
                                   MultiBufferSource buffers, int light);
}

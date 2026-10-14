package io.github.firstone.framework.features.animatium.hand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import io.github.firstone.framework.features.animatium.item.LegacyItemShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import org.joml.Matrix4f;

/**
 * Draws items held in first person where 1.7.10 drew them
 *
 * <p>1.7.10 {@code ItemRenderer.renderItemInFirstPerson} moves the hand exactly like 1.21.1 (the same arm position,
 * equip height, swing and eating motion), but then places the item with fixed code: scale 0.4, and for a flat item
 * {@code translate(0, -0.3, 0)}, scale 1.5, turn 50° around Y and 335° around Z, {@code translate(-0.9375,
 * -0.0625, 0)} before drawing the sprite from (0, 0, 0) to (1, 1, -1/16) mirrored left to right; a block is turned
 * 90° around Y and drawn around its center. The bow while drawing and a blocking sword have their own poses, and a
 * fishing rod or carrot on a stick is turned around. 1.8 replaced that code by the display values of the item models,
 * which is why items sit differently since.</p>
 *
 * <p>How it is drawn: the whole 1.7.10 hand placement is applied here; the item's shape, the move of the 1.21.1 model
 * onto it and the cancelling of the model's display values are shared with the third-person hand
 * ({@link LegacyItemShape}). The riptide spin and scoping keep their 1.21.1 look, as do the items
 * {@link LegacyItemShape} leaves alone. The left hand (left-handed players, the off hand) uses the same placement
 * mirrored.</p>
 */
public final class LegacyFirstPersonItem {

    /** Scale of every held item (1.7.10 {@code glScalef(0.4F)}) */
    private static final float ITEM_SCALE = 0.4F;

    private LegacyFirstPersonItem() {}

    /**
     * Draws a held item with the 1.7.10 placement, if it applies to this item
     *
     * <p>Called at the start of {@code ItemInHandRenderer.renderArmWithItem}, which is left out when this returns
     * true.</p>
     *
     * @param renderer    the game's hand renderer, used to draw the item
     * @param player      the local player
     * @param partialTick progress between ticks
     * @param hand        the hand being drawn
     * @param swing       the swing progress of the hand (0 when not swinging)
     * @param stack       the item in the hand
     * @param equip       how far the item is lowered (0 held up, 1 fully down)
     * @param poseStack   the pose stack, at the camera
     * @param buffers     the buffers to draw into
     * @param light       the packed light
     * @return true if the item was drawn here
     */
    public static boolean render(ItemInHandRenderer renderer, AbstractClientPlayer player, float partialTick,
                                 InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack poseStack,
                                 MultiBufferSource buffers, int light) {
        if (!AnimatiumFeature.getConfig().legacyFirstPersonItems || stack.isEmpty() || player.isScoping()
            || player.isAutoSpinAttack() || !LegacyItemShape.isLegacyItem(stack)) {
            return false;
        }
        boolean using = player.isUsingItem() && player.getUseItemRemainingTicks() > 0
            && player.getUsedItemHand() == hand;
        UseAnim useAnim = using ? stack.getUseAnimation() : UseAnim.NONE;
        if (!isLegacyUse(useAnim)) {
            return false;
        }
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean leftHand = arm == HumanoidArm.LEFT;
        ItemDisplayContext context = leftHand
            ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        // the model the game will draw (same seed as ItemInHandRenderer.renderItem)
        BakedModel model = Minecraft.getInstance().getItemRenderer()
            .getModel(stack, player.level(), player, player.getId() + context.ordinal());
        if (!LegacyItemShape.usesLegacyLook(stack, model)) {
            return false;
        }
        Matrix4f undoDisplay = LegacyItemShape.displayInverse(model, context, leftHand);
        if (undoDisplay == null) {
            return false;
        }
        int side = leftHand ? -1 : 1;
        poseStack.pushPose();
        applyHand(poseStack, player, stack, using, useAnim, partialTick, swing, equip, side);
        LegacyItemShape.applyModelShape(poseStack, model.isGui3d(), side);
        poseStack.mulPose(undoDisplay);
        renderer.renderItem(player, stack, context, leftHand, poseStack, buffers, light);
        poseStack.popPose();
        return true;
    }

    /** Tells whether 1.7.10 had this use animation (newer ones keep their 1.21.1 pose) */
    private static boolean isLegacyUse(UseAnim useAnim) {
        return useAnim == UseAnim.NONE || useAnim == UseAnim.EAT || useAnim == UseAnim.DRINK
            || useAnim == UseAnim.BLOCK || useAnim == UseAnim.BOW;
    }

    /**
     * Applies the 1.7.10 hand placement, from the camera to where the item model is drawn
     *
     * @param using   whether the item is being used (1.7.10 then leaves out the swing's sideways move)
     * @param useAnim the use animation while using, else {@code NONE}
     * @param side    1 for the right hand, -1 for the left hand (everything mirrored across x)
     */
    private static void applyHand(PoseStack poseStack, AbstractClientPlayer player, ItemStack stack, boolean using,
                                  UseAnim useAnim, float partialTick, float swing, float equip, int side) {
        if (using) {
            if (useAnim == UseAnim.EAT || useAnim == UseAnim.DRINK) {
                applyEat(poseStack, player, stack, partialTick, side);
            }
        } else {
            float sqrtSwing = Mth.sqrt(swing);
            poseStack.translate(side * -Mth.sin(sqrtSwing * Mth.PI) * 0.4F, Mth.sin(sqrtSwing * Mth.PI * 2.0F) * 0.2F,
                -Mth.sin(swing * Mth.PI) * 0.2F);
        }
        // 1.7.10: translate(0.7 * 0.8, -0.65 * 0.8 - (1 - equipped) * 0.6, -0.9 * 0.8), then turn 45°
        poseStack.translate(side * 0.56F, -0.52F - equip * 0.6F, -0.72F);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 45.0F));
        float swingSquare = Mth.sin(swing * swing * Mth.PI);
        float swingRoot = Mth.sin(Mth.sqrt(swing) * Mth.PI);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -swingSquare * 20.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * -swingRoot * 20.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-swingRoot * 80.0F));
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        if (useAnim == UseAnim.BLOCK) {
            poseStack.translate(side * -0.5F, 0.2F, 0.0F);
            poseStack.mulPose(Axis.YP.rotationDegrees(side * 30.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(-80.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(side * 60.0F));
        } else if (useAnim == UseAnim.BOW) {
            applyBow(poseStack, player, stack, partialTick, side);
        }
        if (LegacyItemShape.turnsAround(stack)) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        }
    }

    /** 1.7.10 eating and drinking motion (the same as 1.21.1's): the item rises to the mouth and bobs */
    private static void applyEat(PoseStack poseStack, AbstractClientPlayer player, ItemStack stack, float partialTick,
                                 int side) {
        float left = player.getUseItemRemainingTicks() - partialTick + 1.0F;
        float used = 1.0F - left / stack.getUseDuration(player);
        float rest = 1.0F - used;
        rest = rest * rest * rest;
        rest = rest * rest * rest;
        rest = rest * rest * rest;
        float raised = 1.0F - rest;
        float bob = used > 0.2F ? Mth.abs(Mth.cos(left / 4.0F * Mth.PI) * 0.1F) : 0.0F;
        poseStack.translate(0.0F, bob, 0.0F);
        poseStack.translate(side * raised * 0.6F, -raised * 0.5F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * raised * 90.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(raised * 10.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * raised * 30.0F));
    }

    /** 1.7.10 bow while drawing: held across, shaking when fully drawn and stretched by the pull */
    private static void applyBow(PoseStack poseStack, AbstractClientPlayer player, ItemStack stack, float partialTick,
                                 int side) {
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * -18.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -12.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-8.0F));
        poseStack.translate(side * -0.9F, 0.2F, 0.0F);
        float ticks = stack.getUseDuration(player) - (player.getUseItemRemainingTicks() - partialTick + 1.0F);
        float pull = ticks / 20.0F;
        pull = Math.min((pull * pull + pull * 2.0F) / 3.0F, 1.0F);
        if (pull > 0.1F) {
            poseStack.translate(0.0F, Mth.sin((ticks - 0.1F) * 1.3F) * 0.01F * (pull - 0.1F), 0.0F);
        }
        poseStack.translate(0.0F, 0.0F, pull * 0.1F);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * -335.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(side * -50.0F));
        poseStack.translate(0.0F, 0.5F, 0.0F);
        poseStack.scale(1.0F, 1.0F, 1.0F + pull * 0.2F);
        poseStack.translate(0.0F, -0.5F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 50.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 335.0F));
    }
}

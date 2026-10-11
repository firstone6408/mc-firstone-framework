package io.github.firstone.framework.features.animatium.hand;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FoodOnAStickItem;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.item.TridentItem;
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
 * <p>How it is drawn: the whole 1.7.10 placement is applied, the 1.21.1 model is moved onto the 1.7.10 shape (a flat
 * model is centered and faces the other way, a block model shows its front to the north where a 1.7.10 item showed
 * it to the south), and the model's own first-person display values are cancelled by their inverse, so the game
 * still draws the item as a first-person item (same render types and glint).</p>
 *
 * <p>Left as in 1.21.1: items 1.7.10 did not have or drew differently (map, shield, trident, crossbow, spyglass,
 * brush, goat horn), items drawn by code instead of a model (chests, heads, banners…), items whose model a resource
 * pack or another mod changes (with "Resource Pack Items"), the riptide spin and scoping. The left hand (left-handed
 * players, the off hand) uses the same placement mirrored, as 1.21.1 does.</p>
 */
public final class LegacyFirstPersonItem {

    /** Scale of every held item (1.7.10 {@code glScalef(0.4F)}) */
    private static final float ITEM_SCALE = 0.4F;

    /** Scale of a flat item before its sprite is drawn (1.7.10 {@code renderItem}: {@code glScalef(1.5F)}) */
    private static final float FLAT_SCALE = 1.5F;

    /** Half the thickness of a flat item: the 1.21.1 model is centered on it, the 1.7.10 sprite is behind z = 0 */
    private static final float HALF_THICKNESS = 1.0F / 32.0F;

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
        AnimatiumConfig config = AnimatiumFeature.getConfig();
        if (!config.legacyFirstPersonItems || stack.isEmpty() || player.isScoping() || player.isAutoSpinAttack()
            || !isLegacyItem(stack)) {
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
        if (model.isCustomRenderer() || config.resourcePackItems && PackItemModels.changedByPack(stack.getItem())) {
            return false;
        }
        Matrix4f undoDisplay = displayInverse(model, context, leftHand);
        if (undoDisplay == null) {
            return false;
        }
        int side = leftHand ? -1 : 1;
        poseStack.pushPose();
        applyHand(poseStack, player, stack, using, useAnim, partialTick, swing, equip, side);
        applyModelShape(poseStack, model.isGui3d(), side);
        poseStack.mulPose(undoDisplay);
        renderer.renderItem(player, stack, context, leftHand, poseStack, buffers, light);
        poseStack.popPose();
        return true;
    }

    /** Tells whether the item existed in 1.7.10 and was drawn by the common item code there */
    private static boolean isLegacyItem(ItemStack stack) {
        return !(stack.getItem() instanceof MapItem || stack.getItem() instanceof ShieldItem
            || stack.getItem() instanceof TridentItem || stack.getItem() instanceof CrossbowItem
            || stack.getItem() instanceof SpyglassItem || stack.getItem() instanceof BrushItem
            || stack.getItem() instanceof InstrumentItem);
    }

    /** Tells whether 1.7.10 had this use animation (newer ones keep their 1.21.1 pose) */
    private static boolean isLegacyUse(UseAnim useAnim) {
        return useAnim == UseAnim.NONE || useAnim == UseAnim.EAT || useAnim == UseAnim.DRINK
            || useAnim == UseAnim.BLOCK || useAnim == UseAnim.BOW;
    }

    /** Tells whether 1.7.10 turned the item around before drawing it ({@code shouldRotateAroundWhenRendering}) */
    private static boolean turnsAround(ItemStack stack) {
        return stack.getItem() instanceof FishingRodItem || stack.getItem() instanceof FoodOnAStickItem;
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
        if (turnsAround(stack)) {
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

    /**
     * Applies the 1.7.10 placement of the item's shape and moves the 1.21.1 model onto it
     *
     * @param block true for a block model, false for a flat item
     * @param side  1 for the right hand, -1 for the left hand
     */
    private static void applyModelShape(PoseStack poseStack, boolean block, int side) {
        if (block) {
            // 1.7.10 renderBlockAsItem turns a block 90° (its front, to the south, then faces east); the 1.21.1 model
            // shows its front to the north, so it is turned 180° more
            poseStack.mulPose(Axis.YP.rotationDegrees(side * 90.0F + 180.0F));
            return;
        }
        // 1.7.10 renderItem, before renderItemIn2D
        poseStack.translate(0.0F, -0.3F, 0.0F);
        poseStack.scale(FLAT_SCALE, FLAT_SCALE, FLAT_SCALE);
        poseStack.mulPose(Axis.YP.rotationDegrees(side * 50.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 335.0F));
        poseStack.translate(side * -0.9375F, -0.0625F, 0.0F);
        // the 1.21.1 model, centered and drawn with its back where the mirrored 1.7.10 sprite had its front
        poseStack.translate(side * 0.5F, 0.5F, -HALF_THICKNESS);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
    }

    /**
     * Returns the inverse of a model's display values, which cancels them when the game applies them
     *
     * @return the inverse, or null if the display values cannot be undone (scaled to nothing)
     */
    private static Matrix4f displayInverse(BakedModel model, ItemDisplayContext context, boolean leftHand) {
        PoseStack display = new PoseStack();
        model.getTransforms().getTransform(context).apply(leftHand, display);
        Matrix4f matrix = new Matrix4f(display.last().pose());
        if (Math.abs(matrix.determinant()) < 1.0E-6F) {
            return null;
        }
        return matrix.invert();
    }
}

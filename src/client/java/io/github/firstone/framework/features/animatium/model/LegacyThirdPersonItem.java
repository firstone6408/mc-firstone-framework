package io.github.firstone.framework.features.animatium.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import io.github.firstone.framework.features.animatium.item.LegacyItemShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.UseAnim;
import org.joml.Matrix4f;

/**
 * Draws items held in third person where 1.7.10 drew them, and the 1.7.10 sword blocking arm
 *
 * <p>1.7.10 {@code RenderPlayer} and {@code RenderBiped.renderEquippedItems}: from the arm ({@code postRender}),
 * {@code translate(-0.0625, 0.4375, 0.0625)}, then by kind of item — a block: {@code translate(0, 0.1875, -0.3125)},
 * 20° around X, 45° around Y, scale (-0.375, -0.375, 0.375); the bow: {@code translate(0, 0.125, 0.3125)}, -20°
 * around Y, scale (0.625, -0.625, 0.625), -100° around X, 45° around Y; a tool ({@code isFull3D}): a rod turned 180°
 * around Z, a blocking sword turned (-50° Y, -10° X, -60° Z), {@code translate(0, 0.1875, 0)} (skeletons 1.5 pixels
 * further out), scale (0.625, -0.625, 0.625), -100° around X, 45° around Y; any other item:
 * {@code translate(0.25, 0.1875, -0.1875)}, scale 0.375, 60° around Z, -90° around X, 20° around Z. The item's shape
 * then follows ({@link LegacyItemShape}). A blocking player's arm is raised by {@code xRot × 0.5 − 3π/10} and not
 * turned ({@code ModelBiped}, {@code heldItemRight = 3}); 1.21.1 uses its shield pose, turned 30° inwards.</p>
 *
 * <p>Applies to players and to the mobs that held items this way in 1.7.10 (zombies and skeletons, their newer kinds
 * included); newer mobs keep their 1.21.1 look, as do the items {@link LegacyItemShape} leaves alone.</p>
 */
public final class LegacyThirdPersonItem {

    /** Scale of tools and the bow (1.7.10 {@code 0.625F}) */
    private static final float TOOL_SCALE = 0.625F;

    /** Scale of blocks (1.7.10 {@code 0.5F * 0.75F}) and of other items (1.7.10 {@code 0.375F}) */
    private static final float SMALL_SCALE = 0.375F;

    /** How far 1.7.10 skeletons held tools further out than other mobs ({@code RenderSkeleton}: 0.09375) */
    private static final float SKELETON_TOOL_OFFSET = 0.09375F;

    /** How far a blocking arm is raised per held item step (1.7.10 {@code Math.PI / 10}, three steps) */
    private static final float BLOCKING_RAISE = (float) (Math.PI / 10) * 3.0F;

    private LegacyThirdPersonItem() {}

    /**
     * Tells whether this entity holds items the 1.7.10 way
     *
     * @param entity any living entity
     * @return true for players, zombies and skeletons (with their newer kinds)
     */
    public static boolean appliesTo(LivingEntity entity) {
        return entity instanceof Player || entity instanceof Zombie || entity instanceof AbstractSkeleton;
    }

    /**
     * Draws a held item with the 1.7.10 placement, if it applies
     *
     * <p>Called at the start of {@code ItemInHandLayer.renderArmWithItem}, which is left out when this returns
     * true.</p>
     *
     * @param model     the entity's model (posed for this frame)
     * @param renderer  the game's hand renderer, used to draw the item
     * @param entity    the entity holding the item
     * @param stack     the item
     * @param context   the display context (third person, left or right hand)
     * @param arm       the arm holding the item
     * @param poseStack the pose stack, at the model
     * @param buffers   the buffers to draw into
     * @param light     the packed light
     * @return true if the item was drawn here
     */
    public static boolean render(EntityModel<?> model, ItemInHandRenderer renderer, LivingEntity entity,
                                 ItemStack stack, ItemDisplayContext context, HumanoidArm arm, PoseStack poseStack,
                                 MultiBufferSource buffers, int light) {
        if (!AnimatiumFeature.getConfig().legacyThirdPersonItems || stack.isEmpty() || !appliesTo(entity)
            || !(model instanceof HumanoidModel<?> humanoid) || !LegacyItemShape.isLegacyItem(stack)) {
            return false;
        }
        boolean leftHand = arm == HumanoidArm.LEFT;
        // the model the game will draw (same seed as ItemInHandRenderer.renderItem)
        BakedModel itemModel = Minecraft.getInstance().getItemRenderer()
            .getModel(stack, entity.level(), entity, entity.getId() + context.ordinal());
        if (!LegacyItemShape.usesLegacyLook(stack, itemModel)) {
            return false;
        }
        Matrix4f undoDisplay = LegacyItemShape.displayInverse(itemModel, context, leftHand);
        if (undoDisplay == null) {
            return false;
        }
        int side = leftHand ? -1 : 1;
        poseStack.pushPose();
        moveToArm(humanoid, arm, poseStack);
        boolean block = itemModel.isGui3d();
        boolean mirrored = applyHeld(poseStack, entity, stack, arm, block, side);
        LegacyItemShape.applyModelShape(poseStack, block, side);
        if (mirrored) {
            // 1.7.10 flipped tools and the bow with a negative scale, which turns quads inside out (1.21.1 hides the
            // back of item quads, so their edges would look hollow); flipping the flat model across its thickness
            // too turns them back, and changes nothing else: both faces show the same pixel at each point
            poseStack.scale(1.0F, 1.0F, -1.0F);
        }
        poseStack.mulPose(undoDisplay);
        renderer.renderItem(entity, stack, context, leftHand, poseStack, buffers, light);
        poseStack.popPose();
        return true;
    }

    /**
     * Moves to the arm like 1.7.10 {@code ModelRenderer.postRender}: its pivot and turn (a slim player arm keeps its
     * 1.21.1 offset, as 1.7.10 had no slim arms; 1.21.1 also moves skeleton hands out, which 1.7.10 did not)
     */
    private static void moveToArm(HumanoidModel<?> model, HumanoidArm arm, PoseStack poseStack) {
        if (model instanceof PlayerModel<?> player) {
            player.translateToHand(arm, poseStack);
            return;
        }
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        part.translateAndRotate(poseStack);
    }

    /**
     * Applies the 1.7.10 placement in the hand, before the item's shape
     *
     * @return true if the placement mirrors the item (the negative scale of tools and the bow)
     */
    private static boolean applyHeld(PoseStack poseStack, LivingEntity entity, ItemStack stack, HumanoidArm arm,
                                     boolean block, int side) {
        poseStack.translate(side * -0.0625F, 0.4375F, 0.0625F);
        if (block) {
            poseStack.translate(0.0F, 0.1875F, -0.3125F);
            poseStack.mulPose(Axis.XP.rotationDegrees(20.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(side * 45.0F));
            poseStack.scale(-SMALL_SCALE, -SMALL_SCALE, SMALL_SCALE);
            return false;
        }
        if (stack.getItem() instanceof BowItem) {
            poseStack.translate(0.0F, 0.125F, 0.3125F);
            poseStack.mulPose(Axis.YP.rotationDegrees(side * -20.0F));
            poseStack.scale(TOOL_SCALE, -TOOL_SCALE, TOOL_SCALE);
            poseStack.mulPose(Axis.XP.rotationDegrees(-100.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(side * 45.0F));
            return true;
        }
        if (LegacyItemShape.isTool(stack)) {
            if (LegacyItemShape.turnsAround(stack)) {
                poseStack.mulPose(Axis.ZP.rotationDegrees(180.0F));
                poseStack.translate(0.0F, -0.125F, 0.0F);
            }
            if (entity instanceof Player && blocksWith(entity, arm)) {
                poseStack.translate(side * 0.05F, 0.0F, -0.1F);
                poseStack.mulPose(Axis.YP.rotationDegrees(side * -50.0F));
                poseStack.mulPose(Axis.XP.rotationDegrees(-10.0F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(side * -60.0F));
            }
            float out = entity instanceof AbstractSkeleton ? SKELETON_TOOL_OFFSET : 0.0F;
            poseStack.translate(side * out, 0.1875F, 0.0F);
            poseStack.scale(TOOL_SCALE, -TOOL_SCALE, TOOL_SCALE);
            poseStack.mulPose(Axis.XP.rotationDegrees(-100.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(side * 45.0F));
            return true;
        }
        poseStack.translate(side * 0.25F, 0.1875F, -0.1875F);
        poseStack.scale(SMALL_SCALE, SMALL_SCALE, SMALL_SCALE);
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 60.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(side * 20.0F));
        return false;
    }

    /** Tells whether the entity is blocking with the item in this arm's hand (a sword, with the block animation) */
    private static boolean blocksWith(LivingEntity entity, HumanoidArm arm) {
        InteractionHand hand = arm == entity.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        return entity.isUsingItem() && entity.getUsedItemHand() == hand
            && entity.getUseItem().getItem() instanceof SwordItem
            && entity.getUseItem().getUseAnimation() == UseAnim.BLOCK;
    }

    /**
     * Tells whether an arm gets the 1.7.10 sword blocking pose
     *
     * @param entity the entity being drawn
     * @param arm    the arm the model poses
     * @return true for a player blocking with a sword in that arm's hand, with "Legacy Third-Person Items" on
     */
    public static boolean blocksWithSword(LivingEntity entity, HumanoidArm arm) {
        return AnimatiumFeature.getConfig().legacyThirdPersonItems && entity instanceof Player
            && blocksWith(entity, arm);
    }

    /**
     * Raises an arm like a 1.7.10 blocking player's: half the walking swing, three held item steps up, not turned
     *
     * @param arm the arm (right or left)
     */
    public static void poseBlockingArm(ModelPart arm) {
        arm.xRot = arm.xRot * 0.5F - BLOCKING_RAISE;
        arm.yRot = 0.0F;
    }
}

package io.github.firstone.framework.features.animatium.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FoodOnAStickItem;
import net.minecraft.world.item.InstrumentItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SpyglassItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.TridentItem;
import org.joml.Matrix4f;

/**
 * The parts of 1.7.10's held item drawing shared by the first-person and third-person hand
 *
 * <p>1.7.10 {@code ItemRenderer.renderItem} drew a flat item with {@code translate(0, -0.3, 0)}, scale 1.5, a turn of
 * 50° around Y and 335° around Z and {@code translate(-0.9375, -0.0625, 0)}, as a sprite from (0, 0, 0) to
 * (1, 1, -1/16) mirrored left to right; a block was turned 90° around Y and drawn around its center
 * ({@code RenderBlocks.renderBlockAsItem}). The 1.21.1 models are moved onto those shapes here, and their own display
 * values are cancelled by their inverse, so the game still draws them in its usual way for the hand (render types,
 * glint).</p>
 *
 * <p>Items 1.7.10 did not have or drew differently (map, shield, trident, crossbow, spyglass, brush, goat horn), items
 * drawn by code instead of a model and, with "Resource Pack Items", items whose model a pack or another mod changes
 * keep their 1.21.1 look. Left hands use the same placement mirrored.</p>
 */
public final class LegacyItemShape {

    /** Scale of a flat item before its sprite is drawn (1.7.10 {@code renderItem}: {@code glScalef(1.5F)}) */
    private static final float FLAT_SCALE = 1.5F;

    /** Half the thickness of a flat item: the 1.21.1 model is centered on it, the 1.7.10 sprite is behind z = 0 */
    private static final float HALF_THICKNESS = 1.0F / 32.0F;

    private LegacyItemShape() {}

    /**
     * Tells whether the item existed in 1.7.10 and was drawn by the common item code there
     *
     * @param stack the item
     * @return false for maps, shields, tridents, crossbows, spyglasses, brushes and goat horns
     */
    public static boolean isLegacyItem(ItemStack stack) {
        return !(stack.getItem() instanceof MapItem || stack.getItem() instanceof ShieldItem
            || stack.getItem() instanceof TridentItem || stack.getItem() instanceof CrossbowItem
            || stack.getItem() instanceof SpyglassItem || stack.getItem() instanceof BrushItem
            || stack.getItem() instanceof InstrumentItem);
    }

    /**
     * Tells whether 1.7.10 turned the item around before drawing it ({@code shouldRotateAroundWhenRendering})
     *
     * @param stack the item
     * @return true for the fishing rod and the carrot (or fungus) on a stick
     */
    public static boolean turnsAround(ItemStack stack) {
        return stack.getItem() instanceof FishingRodItem || stack.getItem() instanceof FoodOnAStickItem;
    }

    /**
     * Tells whether 1.7.10 held the item like a tool in third person ({@code isFull3D})
     *
     * @param stack the item
     * @return true for swords, tools, rods on a stick, sticks, bones and blaze rods (and the newer breeze rod and mace)
     */
    public static boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof TieredItem || stack.getItem() instanceof FishingRodItem
            || stack.getItem() instanceof FoodOnAStickItem || stack.getItem() instanceof MaceItem
            || stack.is(Items.STICK) || stack.is(Items.BONE) || stack.is(Items.BLAZE_ROD) || stack.is(Items.BREEZE_ROD);
    }

    /**
     * Tells whether the model may get the 1.7.10 placement
     *
     * @param stack the item
     * @param model its model, as the game will draw it
     * @return false for models drawn by code, and for models a pack changes when "Resource Pack Items" is on
     */
    public static boolean usesLegacyLook(ItemStack stack, BakedModel model) {
        return !model.isCustomRenderer()
            && !(AnimatiumFeature.getConfig().resourcePackItems && PackItemModels.changedByPack(stack.getItem()));
    }

    /**
     * Applies the 1.7.10 placement of the item's shape and moves the 1.21.1 model onto it
     *
     * @param poseStack the pose stack, where 1.7.10 called {@code renderItem}
     * @param block     true for a block model, false for a flat item
     * @param side      1 for the right hand, -1 for the left hand
     */
    public static void applyModelShape(PoseStack poseStack, boolean block, int side) {
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
        // the 1.21.1 model, centered and drawn with its back where the mirrored 1.7.10 sprite had its front; in the
        // left hand it is not turned, so the item is the mirror image of the right hand's (a flat model shows the
        // same pixel at each point from both sides, like the 1.21.1 left hand)
        poseStack.translate(side * 0.5F, 0.5F, -HALF_THICKNESS);
        if (side > 0) {
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        }
    }

    /**
     * Returns the inverse of a model's display values, which cancels them when the game applies them
     *
     * @param model    the model
     * @param context  where it is drawn
     * @param leftHand whether it is drawn in the left hand
     * @return the inverse, or null if the display values cannot be undone (scaled to nothing)
     */
    public static Matrix4f displayInverse(BakedModel model, ItemDisplayContext context, boolean leftHand) {
        PoseStack display = new PoseStack();
        model.getTransforms().getTransform(context).apply(leftHand, display);
        Matrix4f matrix = new Matrix4f(display.last().pose());
        if (Math.abs(matrix.determinant()) < 1.0E-6F) {
            return null;
        }
        return matrix.invert();
    }
}

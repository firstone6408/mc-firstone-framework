package io.github.firstone.framework.features.animatium.item;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws items lying on the ground like 1.7.10 ({@code RenderItem.doRender})
 *
 * <p>Both versions bob and spin an item the same way; 1.7.10 drew them differently:</p>
 * <ul>
 *   <li>Blocks always spin, a quarter of their size, around the middle of the item (1.21.1 draws them 0.125
 *       higher). A stack shows 2 to 5 blocks (more than 1, 5, 20, 40 items) scattered up to 0.2 blocks.</li>
 *   <li>Flat items with Fancy or Fabulous graphics spin, half their size. A stack shows 1 to 4 sprites (from 2, 16,
 *       32 items) one right behind the other, without the 1.21.1 jitter.</li>
 *   <li>Flat items with Fast graphics do not spin: each is one flat picture turned to the camera, lit from above,
 *       without enchantment glint, and a stack shows 2 to 5 of them scattered.</li>
 * </ul>
 * <p>The scattering uses the same random numbers for every item (1.7.10 seeds them with 187). Items 1.7.10 did not
 * have, items drawn by code and, with "Resource Pack Items", items whose model a pack changes keep the 1.21.1 look
 * ({@link LegacyItemShape}); a flat model without a front face (not a plain sprite) is drawn as with Fancy graphics.
 * The shadow and name are drawn as in 1.21.1.</p>
 */
public final class LegacyGroundItem {

    /** Height of the 1.7.10 position of an item above its bottom: it was the middle of its box */
    private static final float ITEM_MIDDLE = 0.125F;

    /** Size of a block lying on the ground */
    private static final float BLOCK_SCALE = 0.25F;

    /** Size of a flat item lying on the ground */
    private static final float FLAT_SCALE = 0.5F;

    /** How far block copies are scattered, in blocks */
    private static final float BLOCK_SCATTER = 0.2F;

    /** How far Fast flat copies are scattered, at half size */
    private static final float FAST_SCATTER = 0.3F;

    /** Distance between the sprites of a Fancy stack: the sprite's thickness and a gap (0.0625 + 0.021875) */
    private static final float SPRITE_SPACING = 0.084375F;

    /** Where a Fast picture starts, from the item's middle at half size (1.7.10: x from -0.5, y from -0.25) */
    private static final float PICTURE_LEFT = 0.5F;
    private static final float PICTURE_BOTTOM = 0.25F;

    /** The seed 1.7.10 gives its random numbers before drawing every item */
    private static final long SEED = 187L;

    /** Random numbers for the scattering; used on the render thread only */
    private static final RandomSource SCATTER = RandomSource.create(SEED);

    /** Random numbers to ask a model for its quads (flat item models do not use them) */
    private static final RandomSource QUAD_RANDOM = RandomSource.create(SEED);

    /** Ints per vertex in a model quad, and where its position and texture coordinates are */
    private static final int VERTEX_INTS = DefaultVertexFormat.BLOCK.getVertexSize() / 4;
    private static final int U_INDEX = 4;

    private LegacyGroundItem() {}

    /**
     * Draws an item lying on the ground with the 1.7.10 look, if it applies to this item
     *
     * <p>Called at the start of {@code ItemEntityRenderer.render}; the caller draws the name and leaves the 1.21.1
     * drawing out when this returns true.</p>
     *
     * @param entity       the item
     * @param partialTick  progress between ticks
     * @param poseStack    the pose stack, at the item's position (the bottom of its box)
     * @param buffers      the buffers to draw into
     * @param light        the packed light
     * @param itemRenderer the game's item renderer
     * @param itemColors   the colors the item renderer tints items with
     * @param cameraYaw    the camera's yaw in degrees, which Fast pictures turn to
     * @return true if the item was drawn here
     */
    public static boolean render(ItemEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                                 int light, ItemRenderer itemRenderer, ItemColors itemColors, float cameraYaw) {
        ItemStack stack = entity.getItem();
        if (!AnimatiumFeature.getConfig().legacyGroundItems || stack.isEmpty()
            || !LegacyItemShape.isLegacyItem(stack)) {
            return false;
        }
        // the model the game would draw (same seed as ItemEntityRenderer.render)
        BakedModel model = itemRenderer.getModel(stack, entity.level(), null, entity.getId());
        if (!LegacyItemShape.usesLegacyLook(stack, model)) {
            return false;
        }
        Matrix4f undoGround = LegacyItemShape.displayInverse(model, ItemDisplayContext.GROUND, false);
        if (undoGround == null) {
            return false;
        }
        boolean block = model.isGui3d();
        List<BakedQuad> picture = !block && isFastGraphics() ? frontFaces(model) : List.of();
        SCATTER.setSeed(SEED);
        float age = entity.getAge() + partialTick;
        float bob = Mth.sin(age / 10.0F + entity.bobOffs) * 0.1F + 0.1F;
        float spin = (age / 20.0F + entity.bobOffs) * (180.0F / (float) Math.PI);
        poseStack.pushPose();
        poseStack.translate(0.0F, ITEM_MIDDLE + bob, 0.0F);
        if (block) {
            renderBlocks(stack, model, undoGround, spin, poseStack, buffers, light, itemRenderer);
        } else if (!picture.isEmpty()) {
            renderPictures(stack, picture, itemColors, cameraYaw, poseStack, buffers, light);
        } else {
            renderSprites(stack, model, undoGround, spin, poseStack, buffers, light, itemRenderer);
        }
        poseStack.popPose();
        return true;
    }

    /**
     * Returns how many blocks or Fast pictures 1.7.10 showed for a stack
     *
     * @param count the number of items
     * @return 1, or 2, 3, 4, 5 for more than 1, 5, 20, 40 items
     */
    public static int copies(int count) {
        if (count > 40) {
            return 5;
        }
        if (count > 20) {
            return 4;
        }
        if (count > 5) {
            return 3;
        }
        return count > 1 ? 2 : 1;
    }

    /**
     * Returns how many sprites 1.7.10 showed for a stack of flat items with Fancy graphics
     *
     * @param count the number of items
     * @return 1, or 2, 3, 4 from 2, 16, 32 items
     */
    public static int sprites(int count) {
        if (count < 2) {
            return 1;
        }
        if (count < 16) {
            return 2;
        }
        return count < 32 ? 3 : 4;
    }

    private static boolean isFastGraphics() {
        return Minecraft.getInstance().options.graphicsMode().get() == GraphicsStatus.FAST;
    }

    /** One step of 1.7.10 scattering: from -range to range */
    private static float scatter(float range) {
        return (SCATTER.nextFloat() * 2.0F - 1.0F) * range;
    }

    /** Draws the model where 1.7.10 drew it, cancelling its ground display values */
    private static void renderModel(ItemStack stack, BakedModel model, Matrix4f undoGround, PoseStack poseStack,
                                    MultiBufferSource buffers, int light, ItemRenderer itemRenderer) {
        poseStack.mulPose(undoGround);
        itemRenderer.render(stack, ItemDisplayContext.GROUND, false, poseStack, buffers, light,
            OverlayTexture.NO_OVERLAY, model);
    }

    /** Blocks: spinning, a quarter of their size, scattered copies */
    private static void renderBlocks(ItemStack stack, BakedModel model, Matrix4f undoGround, float spin,
                                     PoseStack poseStack, MultiBufferSource buffers, int light,
                                     ItemRenderer itemRenderer) {
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        poseStack.scale(BLOCK_SCALE, BLOCK_SCALE, BLOCK_SCALE);
        int copies = copies(stack.getCount());
        for (int i = 0; i < copies; i++) {
            poseStack.pushPose();
            if (i > 0) {
                float range = BLOCK_SCATTER / BLOCK_SCALE;
                poseStack.translate(scatter(range), scatter(range), scatter(range));
            }
            // 1.7.10 renderBlockAsItem shows a block's front to the south, the 1.21.1 model to the north
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            renderModel(stack, model, undoGround, poseStack, buffers, light, itemRenderer);
            poseStack.popPose();
        }
    }

    /** Flat items with Fancy graphics: spinning sprites, one right behind the other */
    private static void renderSprites(ItemStack stack, BakedModel model, Matrix4f undoGround, float spin,
                                      PoseStack poseStack, MultiBufferSource buffers, int light,
                                      ItemRenderer itemRenderer) {
        poseStack.scale(FLAT_SCALE, FLAT_SCALE, FLAT_SCALE);
        poseStack.mulPose(Axis.YP.rotationDegrees(spin));
        int sprites = sprites(stack.getCount());
        poseStack.translate(-PICTURE_LEFT, -PICTURE_BOTTOM, -(SPRITE_SPACING * sprites / 2.0F));
        for (int i = 0; i < sprites; i++) {
            poseStack.translate(0.0F, 0.0F, SPRITE_SPACING);
            poseStack.pushPose();
            LegacyItemShape.moveModelOntoSprite(poseStack);
            renderModel(stack, model, undoGround, poseStack, buffers, light, itemRenderer);
            poseStack.popPose();
        }
    }

    /** Flat items with Fast graphics: flat pictures turned to the camera, scattered copies */
    private static void renderPictures(ItemStack stack, List<BakedQuad> picture, ItemColors itemColors,
                                       float cameraYaw, PoseStack poseStack, MultiBufferSource buffers, int light) {
        poseStack.scale(FLAT_SCALE, FLAT_SCALE, FLAT_SCALE);
        // the buffer the game uses for this item on the ground, without the glint 1.7.10 left out here
        VertexConsumer consumer = buffers.getBuffer(ItemBlockRenderTypes.getRenderType(stack, culled(stack)));
        int copies = copies(stack.getCount());
        for (int i = 0; i < copies; i++) {
            poseStack.pushPose();
            if (i > 0) {
                poseStack.translate(scatter(FAST_SCATTER), scatter(FAST_SCATTER), scatter(FAST_SCATTER));
            }
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - cameraYaw));
            drawPicture(stack, picture, itemColors, poseStack.last(), consumer, light);
            poseStack.popPose();
        }
    }

    /** Whether the game draws this item on the ground with the culled sheet ({@code ItemRenderer.render}) */
    private static boolean culled(ItemStack stack) {
        return !(stack.getItem() instanceof BlockItem blockItem)
            || !(blockItem.getBlock() instanceof HalfTransparentBlock)
            && !(blockItem.getBlock() instanceof StainedGlassPaneBlock);
    }

    /** The quads of a flat model seen from its front (south), one per layer; empty if it has none */
    private static List<BakedQuad> frontFaces(BakedModel model) {
        List<BakedQuad> faces = new ArrayList<>();
        for (BakedQuad quad : model.getQuads(null, null, QUAD_RANDOM)) {
            if (quad.getDirection() == Direction.SOUTH) {
                faces.add(quad);
            }
        }
        return faces;
    }

    /**
     * Draws a flat picture of the layers: each front quad laid on z = 0 with the 1.7.10 corners (x from -0.5 to 0.5,
     * y from -0.25 to 0.75), facing +z, lit from above (1.7.10 gave it the normal (0, 1, 0))
     */
    private static void drawPicture(ItemStack stack, List<BakedQuad> picture, ItemColors itemColors,
                                    PoseStack.Pose pose, VertexConsumer consumer, int light) {
        for (BakedQuad quad : picture) {
            int color = quad.isTinted() ? itemColors.getColor(stack, quad.getTintIndex()) : -1;
            int[] data = quad.getVertices();
            for (int vertex = 0; vertex < 4; vertex++) {
                int at = vertex * VERTEX_INTS;
                consumer.addVertex(pose, Float.intBitsToFloat(data[at]) - PICTURE_LEFT,
                        Float.intBitsToFloat(data[at + 1]) - PICTURE_BOTTOM, 0.0F)
                    .setColor(color)
                    .setUv(Float.intBitsToFloat(data[at + U_INDEX]), Float.intBitsToFloat(data[at + U_INDEX + 1]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(light)
                    .setNormal(pose, 0.0F, 1.0F, 0.0F);
            }
        }
    }
}

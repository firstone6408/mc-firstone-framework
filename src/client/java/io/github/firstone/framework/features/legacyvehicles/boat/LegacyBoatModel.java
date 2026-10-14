package io.github.firstone.framework.features.legacyvehicles.boat;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import io.github.firstone.framework.FirstOneFramework;
import io.github.firstone.framework.common.boat.LegacyBoatPhysics;
import io.github.firstone.framework.features.legacyvehicles.LegacyVehiclesConfig;
import io.github.firstone.framework.features.legacyvehicles.LegacyVehiclesFeature;
import io.github.firstone.framework.mixin.boat.BoatAccessor;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.EnumMap;

/**
 * The 1.7.10 boat model ({@code ModelBoat} drawn by {@code RenderBoat}): a flat bottom and four low straight sides,
 * without paddles, covered with the planks of the boat's wood
 *
 * <p>The 1.7.10 boat texture cannot ship with the mod, and its layout does not fit the 1.21.1 textures; the 1.7.10
 * boat texture was a planks pattern, so each face is covered with the planks block texture, one 16-pixel tile per
 * 16 pixels of model. A resource pack that changes planks changes the boat too.</p>
 *
 * <p>When a resource pack changes a boat's own texture, the 1.21.1 model is kept for it (if "Resource Pack Boats"
 * is on), as that texture only fits the 1.21.1 model.</p>
 *
 * <p>Water does not show inside the boat: the inside is covered with a water mask as in 1.21.1. A boat moved with the
 * 1.21.1 motion (without a driver on a server without the mod, or a driver with the 1.21.1 handling) floats lower
 * than a 1.7.10 boat, so it is drawn higher to keep the top of its sides above the water.</p>
 */
public final class LegacyBoatModel {

    /** The model's boxes, in model pixels, as built by 1.7.10 {@code ModelBoat} */
    private static final Part[] PARTS = {
        // bottom: 24 x 16 x 4, laid flat
        new Part(0, 4, 0, (float) (Math.PI / 2), 0, -12, -8, -3, 24, 16, 4),
        // the four sides: 20 x 6 x 2 each, turned around the middle
        new Part(-11, 4, 0, 0, (float) (Math.PI * 3.0 / 2.0), -10, -7, -1, 20, 6, 2),
        new Part(11, 4, 0, 0, (float) (Math.PI / 2), -10, -7, -1, 20, 6, 2),
        new Part(0, 4, -9, 0, (float) Math.PI, -10, -7, -1, 20, 6, 2),
        new Part(0, 4, 9, 0, 0, -10, -7, -1, 20, 6, 2),
    };

    /**
     * The inside of the boat, from its floor to the top of its sides, drawn as a water mask (like the 1.21.1 water
     * patch): water below the top of the sides is not drawn inside the boat
     */
    private static final Part WATER_MASK = new Part(0, 0, 0, 0, 0, -10, -3, -8, 20, 6, 16);

    /**
     * Height of the model above the bottom of the hitbox, for a boat floating like 1.7.10: 1.7.10 drew the boat at its
     * position, 0.3 above it, but boats in 1.7.10 (with OptiFine) look 0.11 lower in the water, with the floor at the
     * water's surface, so the model is drawn that much lower (the hitbox and the rider are not moved)
     */
    private static final double HEIGHT_OFFSET = 0.19;

    /** Height of the top of the sides above the model's origin (3 pixels) */
    private static final double SIDE_TOP = 3.0 / 16.0;

    /**
     * Least height of the top of the sides above the water: about the 1.21.1 boat's; a boat that floats lower is
     * drawn higher, so its water mask stays above the water
     */
    private static final double MIN_FREEBOARD = 0.1;

    /** Whether a resource pack changes the texture of each boat wood, filled when first needed after a reload */
    private static final EnumMap<Boat.Type, Boolean> PACK_TEXTURES = new EnumMap<>(Boat.Type.class);

    private LegacyBoatModel() {}

    /**
     * Makes the resource pack checks start over after every resource reload
     *
     * <p>Called once by {@link LegacyVehiclesFeature#initializeClient()}.</p>
     */
    public static void registerReloadListener() {
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(
            new SimpleSynchronousResourceReloadListener() {
                @Override
                public ResourceLocation getFabricId() {
                    return ResourceLocation.fromNamespaceAndPath(FirstOneFramework.MOD_ID, "legacy_boat_model");
                }

                @Override
                public void onResourceManagerReload(ResourceManager resourceManager) {
                    PACK_TEXTURES.clear();
                }
            });
    }

    /**
     * Tells whether a boat is drawn with the 1.7.10 model
     *
     * @param boat the boat about to be drawn
     * @return true for a wooden boat when "Legacy Model" is on, unless a resource pack changes its texture and
     *         "Resource Pack Boats" is on
     */
    public static boolean draws(Boat boat) {
        LegacyVehiclesConfig config = LegacyVehiclesFeature.getConfig();
        return config.legacyBoatModel && LegacyBoatPhysics.isWoodenBoat(boat)
            && !(config.resourcePackBoats && packTexture(boat.getVariant()));
    }

    /**
     * Draws the 1.7.10 model of a boat, with the 1.7.10 wobble after a hit and a water mask inside
     *
     * @param boat        the boat
     * @param yaw         the boat's yaw for this frame
     * @param partialTick part of the tick passed, for the wobble
     * @param poseStack   pose at the boat's position
     * @param buffers     buffers of the frame
     * @param light       packed light at the boat
     */
    public static void render(Boat boat, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                              int light) {
        poseStack.pushPose();
        poseStack.translate(0.0, heightOffset(boat), 0.0);
        // 1.7.10 turned the model by 180 - yaw, with a yaw 90 degrees before the 1.21.1 one
        poseStack.mulPose(Axis.YP.rotationDegrees(270.0F - yaw));
        float hurt = boat.getHurtTime() - partialTick;
        float damage = Math.max(0.0F, boat.getDamage() - partialTick);
        if (hurt > 0.0F) {
            poseStack.mulPose(Axis.XP.rotationDegrees(Mth.sin(hurt) * hurt * damage / 10.0F * boat.getHurtDir()));
        }
        float bubble = boat.getBubbleAngle(partialTick);
        if (!Mth.equal(bubble, 0.0F)) {
            poseStack.mulPose(new Quaternionf().setAngleAxis(bubble * Mth.DEG_TO_RAD, 1.0F, 0.0F, 1.0F));
        }
        poseStack.scale(-1.0F, -1.0F, 1.0F);

        TextureAtlasSprite planks = Minecraft.getInstance().getBlockRenderer()
            .getBlockModel(boat.getVariant().getPlanks().defaultBlockState()).getParticleIcon();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        for (Part part : PARTS) {
            part.render(poseStack, consumer, planks, light, false);
        }
        if (!boat.isUnderWater()) {
            WATER_MASK.render(poseStack, buffers.getBuffer(RenderType.waterMask()), planks, light, true);
        }
        poseStack.popPose();
    }

    /**
     * Returns the model's height above the bottom of the hitbox: {@value #HEIGHT_OFFSET}, or more for a boat that
     * floats lower than a 1.7.10 boat (a boat moved with the 1.21.1 motion), so the top of its sides stays
     * {@value #MIN_FREEBOARD} above the water
     */
    private static double heightOffset(Boat boat) {
        if (!LegacyBoatPhysics.isFloating(boat)) {
            return HEIGHT_OFFSET;
        }
        double needed = ((BoatAccessor) boat).framework$getWaterLevel() + MIN_FREEBOARD - SIDE_TOP - boat.getY();
        return Math.max(HEIGHT_OFFSET, needed);
    }

    /** Tells whether a resource pack (not the game itself) gives the boat texture of a wood */
    private static boolean packTexture(Boat.Type type) {
        return PACK_TEXTURES.computeIfAbsent(type, wood -> Minecraft.getInstance().getResourceManager()
            .getResource(ResourceLocation.withDefaultNamespace("textures/entity/boat/" + wood.getName() + ".png"))
            .map(resource -> !"vanilla".equals(resource.sourcePackId()))
            .orElse(false));
    }

    /**
     * One box of the model, like a 1.7.10 {@code ModelRenderer} with one box: a pivot, rotations around X and Y,
     * and the box (corner and size) in pixels
     */
    private record Part(float pivotX, float pivotY, float pivotZ, float rotX, float rotY,
                        float x, float y, float z, float width, float height, float depth) {

        /**
         * Draws the box's six faces, each covered with 16-pixel planks tiles; with {@code bothSides}, every quad is
         * also drawn facing the other way (for the water mask, whose render type hides back faces)
         */
        void render(PoseStack poseStack, VertexConsumer consumer, TextureAtlasSprite planks, int light,
                    boolean bothSides) {
            poseStack.pushPose();
            poseStack.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
            poseStack.translate(this.pivotX, this.pivotY, this.pivotZ);
            poseStack.mulPose(Axis.YP.rotation(this.rotY));
            poseStack.mulPose(Axis.XP.rotation(this.rotX));
            PoseStack.Pose pose = poseStack.last();
            float x1 = this.x + this.width;
            float y1 = this.y + this.height;
            float z1 = this.z + this.depth;
            // each face: a corner, then its two edges (along u and along v), then its outward normal
            Face face = new Face(pose, consumer, planks, light, bothSides);
            face.draw(this.x, this.y, this.z, 0, 0, this.depth, this.width, 0, 0, 0, -1, 0);
            face.draw(this.x, y1, this.z, this.width, 0, 0, 0, 0, this.depth, 0, 1, 0);
            face.draw(this.x, this.y, this.z, this.width, 0, 0, 0, this.height, 0, 0, 0, -1);
            face.draw(this.x, this.y, z1, 0, this.height, 0, this.width, 0, 0, 0, 0, 1);
            face.draw(this.x, this.y, this.z, 0, this.height, 0, 0, 0, this.depth, -1, 0, 0);
            face.draw(x1, this.y, this.z, 0, 0, this.depth, 0, this.height, 0, 1, 0, 0);
            poseStack.popPose();
        }
    }

    /** Draws faces of a part with the part's pose, texture and light */
    private record Face(PoseStack.Pose pose, VertexConsumer consumer, TextureAtlasSprite planks, int light,
                        boolean bothSides) {

        /**
         * Draws one face, from corner {@code (x, y, z)} along edges {@code a} and {@code b}, split into tiles of at
         * most 16 x 16 pixels so the planks keep their size
         */
        void draw(float x, float y, float z, float ax, float ay, float az, float bx, float by, float bz,
                  float nx, float ny, float nz) {
            float lengthA = Math.abs(ax + ay + az);
            float lengthB = Math.abs(bx + by + bz);
            Vector3f corner = new Vector3f(x, y, z);
            Vector3f edgeA = new Vector3f(ax, ay, az).div(lengthA);
            Vector3f edgeB = new Vector3f(bx, by, bz).div(lengthB);
            Vector3f normal = new Vector3f(nx, ny, nz);
            float u0 = this.planks.getU(0);
            float v0 = this.planks.getV(0);
            for (float a = 0; a < lengthA; a += 16) {
                float a1 = Math.min(a + 16, lengthA);
                for (float b = 0; b < lengthB; b += 16) {
                    float b1 = Math.min(b + 16, lengthB);
                    float u1 = this.planks.getU((a1 - a) / 16.0F);
                    float v1 = this.planks.getV((b1 - b) / 16.0F);
                    vertex(corner, edgeA, a, edgeB, b, u0, v0, normal);
                    vertex(corner, edgeA, a1, edgeB, b, u1, v0, normal);
                    vertex(corner, edgeA, a1, edgeB, b1, u1, v1, normal);
                    vertex(corner, edgeA, a, edgeB, b1, u0, v1, normal);
                    if (this.bothSides) {
                        vertex(corner, edgeA, a, edgeB, b1, u0, v1, normal);
                        vertex(corner, edgeA, a1, edgeB, b1, u1, v1, normal);
                        vertex(corner, edgeA, a1, edgeB, b, u1, v0, normal);
                        vertex(corner, edgeA, a, edgeB, b, u0, v0, normal);
                    }
                }
            }
        }

        /** Adds the vertex {@code a} pixels along edge A and {@code b} pixels along edge B from the corner */
        private void vertex(Vector3f corner, Vector3f edgeA, float a, Vector3f edgeB, float b, float u, float v,
                            Vector3f normal) {
            this.consumer.addVertex(this.pose, corner.x + edgeA.x * a + edgeB.x * b,
                    corner.y + edgeA.y * a + edgeB.y * b, corner.z + edgeA.z * a + edgeB.z * b)
                .setColor(-1)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(this.light)
                .setNormal(this.pose, normal.x, normal.y, normal.z);
        }
    }
}

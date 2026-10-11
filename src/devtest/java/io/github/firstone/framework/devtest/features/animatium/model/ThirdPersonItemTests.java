package io.github.firstone.framework.devtest.features.animatium.model;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.firstone.framework.devtest.core.Images;
import io.github.firstone.framework.devtest.core.Spawned;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.core.TestResourcePack;
import io.github.firstone.framework.devtest.core.VertexCapture;
import io.github.firstone.framework.devtest.core.VertexCapture.Vertex;
import io.github.firstone.framework.devtest.mixin.ItemInHandLayerInvoker;
import io.github.firstone.framework.devtest.mixin.LivingEntityRendererAccessor;
import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsConfig;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.minecraft.client.CameraType;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.ELSEWHERE;
import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.blockCorners;
import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.checkAt;
import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.farthestCorner;
import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.flatCorners;
import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.rotate;
import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.samePositions;
import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.spriteLikeLegacy;

/**
 * Tests of the 1.7.10 third-person items and sword blocking arm, part of the Animatium tests
 *
 * <p>The entity is drawn once (which poses its model for the frame), then its held item layer draws one arm's item
 * into a {@link VertexCapture}. The vertices are compared with the 1.7.10 placement written again here from
 * {@code RenderPlayer} and {@code RenderBiped.renderEquippedItems}: from the arm ({@code postRender}),
 * {@code translate(-0.0625, 0.4375, 0.0625)}, then by kind of item, then the item's shape ({@code renderItem}).</p>
 */
public final class ThirdPersonItemTests {

    /** Share of pixels, in percent, around the player that must differ between the 1.7.10 and the 1.21.1 view */
    private static final double VIEWS_DIFFER_PERCENT = 0.3;

    /** 1.7.10 arm raise of a blocking player: {@code Math.PI / 10} times 3 held item steps */
    private static final double BLOCKING_RAISE = Math.PI / 10.0 * 3.0;

    private ThirdPersonItemTests() {}

    /**
     * Returns the third-person item tests
     *
     * @return the tests, in the order they run
     */
    public static List<TestCase> tests() {
        return List.of(
            TestCase.of("Third-Person Items: a player holds tools, the bow, flat items and blocks like 1.7.10; off: "
                + "1.21.1", ThirdPersonItemTests::playerRightHand),
            TestCase.of("Third-Person Items: the left hand gets the same placement mirrored",
                ThirdPersonItemTests::playerLeftHand),
            TestCase.of("Third-Person Items: zombies and skeletons hold items like 1.7.10 (skeletons further out)",
                ThirdPersonItemTests::mobs),
            TestCase.of("Third-Person Items: a blocking sword and arm like 1.7.10; off: the 1.21.1 shield pose",
                ThirdPersonItemTests::swordBlocking),
            TestCase.of("Third-Person Items: newer items and newer mobs keep their 1.21.1 look",
                ThirdPersonItemTests::newerItemsAndMobs),
            TestCase.of("Third-Person Items: an item whose model a pack changes keeps the pack's look",
                ThirdPersonItemTests::resourcePackItems),
            TestCase.slow("Third-Person Items: screenshots from the front, on and off (to compare with 1.7.10)",
                ThirdPersonItemTests::screenshots)
        );
    }

    private static AnimatiumConfig config() {
        return AnimatiumFeature.getConfig();
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    /** One arm's item as drawn by the held item layer, and where the arm was */
    private record Held(List<Vertex> drawn, Matrix4f arm) {}

    /**
     * Draws the entity once (posing its model), then one arm's item on its own
     *
     * @param legacyArm true to expect the arm's own pivot and turn (1.7.10 {@code postRender}), false to use the
     *                  model's hand position (players: slim arms keep their offset)
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Held drawHeld(TestContext t, LivingEntity entity, HumanoidArm arm, boolean legacyArm) {
        return t.onClientThread(() -> {
            LivingEntityRenderer renderer = (LivingEntityRenderer) t.mc().getEntityRenderDispatcher()
                .getRenderer(entity);
            renderer.render(entity, entity.getYRot(), 1.0F, new PoseStack(), new VertexCapture(),
                LightTexture.FULL_BRIGHT);
            ItemInHandLayer<?, ?> layer = null;
            for (RenderLayer<?, ?> candidate : ((LivingEntityRendererAccessor) renderer).devtest$getLayers()) {
                if (candidate instanceof ItemInHandLayer<?, ?> found) {
                    layer = found;
                }
            }
            t.require(layer != null, "The renderer has no held item layer");
            ItemStack stack = arm == entity.getMainArm() ? entity.getMainHandItem() : entity.getOffhandItem();
            VertexCapture capture = new VertexCapture();
            ((ItemInHandLayerInvoker) layer).devtest$renderArmWithItem(entity, stack, arm == HumanoidArm.LEFT
                ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, arm,
                new PoseStack(), capture, LightTexture.FULL_BRIGHT);
            // where the arm is (only for humanoid models; newer mobs are only compared on and off)
            PoseStack armPose = new PoseStack();
            if (!legacyArm && renderer.getModel() instanceof PlayerModel<?> player) {
                player.translateToHand(arm, armPose);
            } else if (renderer.getModel() instanceof HumanoidModel<?> model) {
                (arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm).translateAndRotate(armPose);
            }
            return new Held(capture.vertices(), new Matrix4f(armPose.last().pose()));
        });
    }

    // ---- the 1.7.10 placement, written again ----------------------------------------------------------------------

    /** Kinds of items 1.7.10 placed differently in third person */
    private enum Kind { BLOCK, BOW, TOOL, FLAT }

    /**
     * The matrix 1.7.10 called {@code renderItem} with, from the arm
     *
     * @param arm          the arm's pivot and turn
     * @param side         1 for the right arm, -1 for the left (mirrored)
     * @param turnsAround  a fishing rod (turned 180° around Z)
     * @param blocking     a player blocking with a sword
     * @param skeleton     a skeleton (tools 0.09375 further out)
     */
    private static Matrix4f legacyHeld(Matrix4f arm, Kind kind, int side, boolean turnsAround, boolean blocking,
                                       boolean skeleton) {
        Matrix4f m = new Matrix4f(arm);
        m.translate(side * -0.0625F, 0.4375F, 0.0625F);
        switch (kind) {
            case BLOCK -> {
                m.translate(0.0F, 0.1875F, -0.3125F);
                rotate(m, 20.0F, 1, 0, 0);
                rotate(m, side * 45.0F, 0, 1, 0);
                m.scale(-0.375F, -0.375F, 0.375F);
            }
            case BOW -> {
                m.translate(0.0F, 0.125F, 0.3125F);
                rotate(m, side * -20.0F, 0, 1, 0);
                m.scale(0.625F, -0.625F, 0.625F);
                rotate(m, -100.0F, 1, 0, 0);
                rotate(m, side * 45.0F, 0, 1, 0);
            }
            case TOOL -> {
                if (turnsAround) {
                    rotate(m, 180.0F, 0, 0, 1);
                    m.translate(0.0F, -0.125F, 0.0F);
                }
                if (blocking) {
                    m.translate(side * 0.05F, 0.0F, -0.1F);
                    rotate(m, side * -50.0F, 0, 1, 0);
                    rotate(m, -10.0F, 1, 0, 0);
                    rotate(m, side * -60.0F, 0, 0, 1);
                }
                m.translate(skeleton ? side * 0.09375F : 0.0F, 0.1875F, 0.0F);
                m.scale(0.625F, -0.625F, 0.625F);
                rotate(m, -100.0F, 1, 0, 0);
                rotate(m, side * 45.0F, 0, 1, 0);
            }
            case FLAT -> {
                m.translate(side * 0.25F, 0.1875F, -0.1875F);
                m.scale(0.375F);
                rotate(m, side * 60.0F, 0, 0, 1);
                rotate(m, -90.0F, 1, 0, 0);
                rotate(m, side * 20.0F, 0, 0, 1);
            }
        }
        return m;
    }

    private static List<Vector3f> expected(Matrix4f held, Kind kind, int side) {
        return kind == Kind.BLOCK ? blockCorners(held) : flatCorners(held, side);
    }

    /** Checks one held item: every 1.7.10 corner drawn, nothing outside, and for flat items the texture's side */
    private static void checkHeld(TestContext t, String name, Held held, Kind kind, int side, boolean turnsAround,
                                  boolean blocking, boolean skeleton) {
        Matrix4f m = legacyHeld(held.arm(), kind, side, turnsAround, blocking, skeleton);
        checkAt(t, name, held.drawn(), expected(m, kind, side));
        if (kind != Kind.BLOCK) {
            t.check(name + ": the texture lies like 1.7.10's", spriteLikeLegacy(held.drawn(), m, side)).isTrue();
        }
    }

    // ---- tests ----------------------------------------------------------------------------------------------------

    private record Item(ItemStack stack, Kind kind, boolean turnsAround) {}

    private static final Item[] ITEMS = {
        new Item(new ItemStack(Items.DIAMOND_SWORD), Kind.TOOL, false),
        new Item(new ItemStack(Items.DIAMOND_PICKAXE), Kind.TOOL, false),
        new Item(new ItemStack(Items.STICK), Kind.TOOL, false),
        new Item(new ItemStack(Items.FISHING_ROD), Kind.TOOL, true),
        new Item(new ItemStack(Items.BOW), Kind.BOW, false),
        new Item(new ItemStack(Items.APPLE), Kind.FLAT, false),
        new Item(new ItemStack(Items.TORCH), Kind.FLAT, false),
        new Item(new ItemStack(Items.COBBLESTONE), Kind.BLOCK, false)};

    private static void standInView(TestContext t) {
        t.teleport(4.5, 1.0, 4.5, 0.0F, 0.0F);
        t.setFlying(true);
    }

    private static void playerRightHand(TestContext t) {
        standInView(t);
        for (Item item : ITEMS) {
            t.holdItem(item.stack());
            t.ticks(3);
            String name = "on, " + item.stack().getHoverName().getString();
            checkHeld(t, name, drawHeld(t, t.player(), HumanoidArm.RIGHT, false), item.kind(), 1,
                item.turnsAround(), false, false);
        }
        config().legacyThirdPersonItems = false;
        t.holdItem(new ItemStack(Items.DIAMOND_SWORD));
        t.ticks(3);
        Held off = drawHeld(t, t.player(), HumanoidArm.RIGHT, false);
        t.check("off, Diamond Sword: farthest 1.7.10 corner (somewhere else, 1.21.1)", farthestCorner(off.drawn(),
            expected(legacyHeld(off.arm(), Kind.TOOL, 1, false, false, false), Kind.TOOL, 1))).atLeast(ELSEWHERE);
    }

    private static void playerLeftHand(TestContext t) {
        standInView(t);
        t.holdItem(ItemStack.EMPTY);
        for (Item item : ITEMS) {
            t.withServerPlayer(player -> player.setItemInHand(InteractionHand.OFF_HAND, item.stack().copy()));
            t.ticks(3);
            String name = "on, off hand (left), " + item.stack().getHoverName().getString();
            checkHeld(t, name, drawHeld(t, t.player(), HumanoidArm.LEFT, false), item.kind(), -1,
                item.turnsAround(), false, false);
        }
    }

    /** Spawns a mob that stays (no AI, kept) with an iron sword, facing the player */
    private static Mob swordMob(TestContext t, EntityType<? extends Mob> type, double x) {
        Spawned<? extends Mob> spawned = t.spawn(type, x, 0.0, 7.5, 180.0F, ThirdPersonItemTests::armWithSword);
        t.ticks(3);
        Mob mob = spawned.client();
        t.require(mob != null, "The mob is gone");
        return mob;
    }

    private static void armWithSword(Mob mob) {
        mob.setNoAi(true);
        mob.setPersistenceRequired();
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
    }

    private static void checkMob(TestContext t, EntityType<? extends Mob> type, double x, boolean skeleton) {
        Mob mob = swordMob(t, type, x);
        String name = "on, " + type.getDescription().getString() + " with an iron sword";
        checkHeld(t, name, drawHeld(t, mob, HumanoidArm.RIGHT, true), Kind.TOOL, 1, false, false, skeleton);
    }

    private static void mobs(TestContext t) {
        t.difficulty(Difficulty.EASY);
        standInView(t);
        checkMob(t, EntityType.ZOMBIE, 2.5, false);
        checkMob(t, EntityType.HUSK, 4.5, false);
        checkMob(t, EntityType.SKELETON, 6.5, true);
        checkMob(t, EntityType.WITHER_SKELETON, 8.5, true);
        checkMob(t, EntityType.ZOMBIFIED_PIGLIN, 10.5, false);
    }

    private static void swordBlocking(TestContext t) {
        LegacyMechanicsConfig mechanics = LegacyMechanicsFeature.getConfig();
        boolean blocking = mechanics.legacySwordBlocking;
        mechanics.legacySwordBlocking = true;
        try {
            standInView(t);
            t.holdItem(new ItemStack(Items.DIAMOND_SWORD));
            t.ticks(3);
            t.hold(t.mc().options.keyUse);
            t.ticks(8);
            t.require(t.onClientThread(() -> t.player().isUsingItem()), "The sword is not blocking");
            Held held = drawHeld(t, t.player(), HumanoidArm.RIGHT, false);
            checkHeld(t, "on, blocking", held, Kind.TOOL, 1, false, true, false);
            float[] arm = rightArm(t);
            t.check("on: blocking arm not turned inwards (1.7.10: 0)", (double) arm[1]).near(0.0, 1.0E-6);
            // 1.7.10 ModelBiped: half the walking swing, minus 3 held item steps, then the idle bob
            double walking = Math.cos(arm[3] * 0.6662F + Math.PI) * 2.0 * arm[4] * 0.5;
            double expected = walking * 0.5 - BLOCKING_RAISE + Math.sin(arm[5] * 0.067F) * 0.05;
            t.check("on: blocking arm raised (1.7.10: half the walk swing - 3π/10, then the idle bob)",
                (double) arm[0]).near(expected, 1.0E-4);
            config().legacyThirdPersonItems = false;
            float[] off = rightArm(t);
            // 1.21.1 shield pose: 30° inwards, plus the head's turn from the body (up to 30° either way)
            double headTurn = Math.max(-Math.PI / 6.0, Math.min(Math.PI / 6.0, off[2]));
            t.check("off: blocking arm turned inwards (1.21.1 shield pose: -30° and the head's turn)",
                (double) off[1]).near(-Math.PI / 6.0 + headTurn, 1.0E-3);
            t.release(t.mc().options.keyUse);
            t.ticks(5);
        } finally {
            mechanics.legacySwordBlocking = blocking;
        }
    }

    /**
     * Poses the local player's model for a frame (partial tick 1); returns its right arm's {xRot, yRot}, the head's
     * yRot, and what the pose was made from: {walk position, walk speed, age in ticks}
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static float[] rightArm(TestContext t) {
        return t.onClientThread(() -> {
            LivingEntityRenderer renderer = (LivingEntityRenderer) t.mc().getEntityRenderDispatcher()
                .getRenderer(t.player());
            renderer.render(t.player(), t.player().getYRot(), 1.0F, new PoseStack(), new VertexCapture(),
                LightTexture.FULL_BRIGHT);
            HumanoidModel<?> model = (HumanoidModel<?>) renderer.getModel();
            LocalPlayer player = t.player();
            return new float[] {model.rightArm.xRot, model.rightArm.yRot, model.head.yRot,
                player.walkAnimation.position(1.0F), Math.min(player.walkAnimation.speed(1.0F), 1.0F),
                player.tickCount + 1.0F};
        });
    }

    private static List<Vertex> drawnOnAndOff(TestContext t, LivingEntity entity, boolean on) {
        config().legacyThirdPersonItems = on;
        try {
            return drawHeld(t, entity, HumanoidArm.RIGHT, false).drawn();
        } finally {
            config().legacyThirdPersonItems = true;
        }
    }

    private static void newerItemsAndMobs(TestContext t) {
        t.difficulty(Difficulty.EASY);
        standInView(t);
        for (ItemStack stack : List.of(new ItemStack(Items.SHIELD), new ItemStack(Items.TRIDENT),
            new ItemStack(Items.CROSSBOW))) {
            t.holdItem(stack);
            t.ticks(3);
            t.check(stack.getHoverName().getString() + ": drawn the same on and off",
                samePositions(drawnOnAndOff(t, t.player(), true), drawnOnAndOff(t, t.player(), false))).isTrue();
        }
        for (EntityType<? extends Mob> type : List.of(EntityType.PIGLIN, EntityType.VINDICATOR)) {
            Mob mob = swordMob(t, type, 6.5);
            t.check(type.getDescription().getString() + " with an iron sword: drawn the same on and off",
                samePositions(drawnOnAndOff(t, mob, true), drawnOnAndOff(t, mob, false))).isTrue();
            t.removeEntities();
        }
    }

    private static void resourcePackItems(TestContext t) throws Exception {
        standInView(t);
        TestResourcePack pack = t.resourcePack("handheld_model")
            .text("assets/minecraft/models/item/handheld.json", "{\"parent\":\"minecraft:item/generated\","
                + "\"display\":{\"thirdperson_righthand\":{\"rotation\":[0,-90,55],\"translation\":[0,4,0.5],"
                + "\"scale\":[0.85,0.85,0.85]}}}");
        pack.enable();
        t.holdItem(new ItemStack(Items.DIAMOND_SWORD));
        t.ticks(3);
        t.check("pack on, Diamond Sword: drawn as without 1.7.10 items (the pack's look)",
            samePositions(drawnOnAndOff(t, t.player(), true), drawnOnAndOff(t, t.player(), false))).isTrue();
        config().resourcePackItems = false;
        checkHeld(t, "pack on, Resource Pack Items off, Diamond Sword", drawHeld(t, t.player(), HumanoidArm.RIGHT,
            false), Kind.TOOL, 1, false, false, false);
        config().resourcePackItems = true;
    }

    /** Captures the view with 1.7.10 third-person items on and off, saves both and checks the player differs */
    private static void compareViews(TestContext t, String name) {
        config().legacyThirdPersonItems = true;
        try (NativeImage on = t.captureFrame()) {
            config().legacyThirdPersonItems = false;
            try (NativeImage off = t.captureFrame()) {
                t.saveFrame("tps_" + name + "_on", on);
                t.saveFrame("tps_" + name + "_off", off);
                int width = on.getWidth() / 4;
                int height = on.getHeight() / 2;
                t.check(name + ": differing pixels around the player between on and off, %", Images.differingPercent(
                    on, off, (on.getWidth() - width) / 2, (on.getHeight() - height) / 2, width, height))
                    .atLeast(VIEWS_DIFFER_PERCENT);
            }
        } finally {
            config().legacyThirdPersonItems = true;
        }
    }

    private static void screenshots(TestContext t) {
        LegacyMechanicsConfig mechanics = LegacyMechanicsFeature.getConfig();
        boolean blocking = mechanics.legacySwordBlocking;
        mechanics.legacySwordBlocking = true;
        try {
            standInView(t);
            t.mc().options.setCameraType(CameraType.THIRD_PERSON_FRONT);
            for (ItemStack stack : List.of(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND_PICKAXE),
                new ItemStack(Items.COBBLESTONE), new ItemStack(Items.APPLE), new ItemStack(Items.BOW),
                new ItemStack(Items.FISHING_ROD))) {
                t.holdItem(stack);
                t.ticks(10);
                compareViews(t, BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
            }
            t.holdItem(new ItemStack(Items.DIAMOND_SWORD));
            t.ticks(5);
            t.hold(t.mc().options.keyUse);
            t.ticks(10);
            compareViews(t, "sword_blocking");
            t.release(t.mc().options.keyUse);
            t.ticks(5);
        } finally {
            mechanics.legacySwordBlocking = blocking;
        }
    }
}

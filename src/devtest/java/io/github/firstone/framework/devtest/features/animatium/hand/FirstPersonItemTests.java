package io.github.firstone.framework.devtest.features.animatium.hand;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.firstone.framework.devtest.core.Images;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.core.TestResourcePack;
import io.github.firstone.framework.devtest.core.VertexCapture;
import io.github.firstone.framework.devtest.core.VertexCapture.Vertex;
import io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks;
import io.github.firstone.framework.devtest.mixin.ItemInHandRendererInvoker;
import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsConfig;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.ELSEWHERE;
import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.blockCorners;
import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.checkAt;
import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.farthestCorner;
import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.rotate;
import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.samePositions;
import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.spriteLikeLegacy;

/**
 * Tests of the 1.7.10 first-person items, part of the Animatium tests
 *
 * <p>The game's own hand code draws one hand into a {@link VertexCapture}; the vertices are compared with the 1.7.10
 * placement written again here from {@code ItemRenderer.renderItemInFirstPerson} and {@code renderItem}, in the order
 * of their GL calls. A flat 1.7.10 item is a sprite from (0, 0, 0) to (1, 1, -1/16), mirrored left to right; a block is
 * a cube around its center, turned 90° around Y with its front (south) to the east. Every corner of that shape must be
 * a drawn vertex, and nothing may be drawn outside it.</p>
 */
public final class FirstPersonItemTests {

    private FirstPersonItemTests() {}

    /**
     * Returns the first-person item tests
     *
     * @return the tests, in the order they run
     */
    public static List<TestCase> tests() {
        return List.of(
            TestCase.of("First-Person Items: a flat item and a tool sit where 1.7.10 drew them, mirrored like 1.7.10; "
                + "off: 1.21.1", FirstPersonItemTests::flatItems),
            TestCase.of("First-Person Items: a block sits and faces like 1.7.10", FirstPersonItemTests::block),
            TestCase.of("First-Person Items: swinging and re-equipping move the item like 1.7.10",
                FirstPersonItemTests::swingAndEquip),
            TestCase.of("First-Person Items: the fishing rod is turned around like 1.7.10",
                FirstPersonItemTests::fishingRod),
            TestCase.of("First-Person Items: the bow while drawing and the eating motion follow 1.7.10",
                FirstPersonItemTests::useAnimations),
            TestCase.of("First-Person Items: a blocking sword takes the 1.7.10 block pose, also while swinging "
                + "(block-hit)", FirstPersonItemTests::swordBlocking),
            TestCase.of("First-Person Items: the left hand gets the same placement mirrored",
                FirstPersonItemTests::leftHand),
            TestCase.of("First-Person Items: shield, trident and crossbow keep their 1.21.1 look",
                FirstPersonItemTests::newerItems),
            TestCase.of("Resource Pack Items: items whose model or model parent a pack changes keep the pack's look",
                FirstPersonItemTests::resourcePackItems),
            TestCase.slow("First-Person Items: screenshots of held items, on and off (to compare with 1.7.10)",
                FirstPersonItemTests::screenshots)
        );
    }

    private static AnimatiumConfig config() {
        return AnimatiumFeature.getConfig();
    }

    // ---- drawing --------------------------------------------------------------------------------------------------

    /** Draws one hand of the local player with the game's hand code; returns the vertices it drew */
    private static List<Vertex> draw(TestContext t, InteractionHand hand, float swing, float equip) {
        return t.onClientThread(() -> {
            LocalPlayer player = t.player();
            VertexCapture capture = new VertexCapture();
            ((ItemInHandRendererInvoker) t.mc().gameRenderer.itemInHandRenderer).devtest$renderArmWithItem(player,
                1.0F, 0.0F, hand, swing, player.getItemInHand(hand), equip, new PoseStack(), capture,
                LightTexture.FULL_BRIGHT);
            return capture.vertices();
        });
    }

    private static List<Vertex> draw(TestContext t) {
        return draw(t, InteractionHand.MAIN_HAND, 0.0F, 0.0F);
    }

    private static void hold(TestContext t, ItemStack stack) {
        t.holdItem(stack);
        t.ticks(3);
    }

    // ---- the 1.7.10 placement, written again ----------------------------------------------------------------------

    /** What the hand is doing, for the placement */
    private record Use(boolean using, boolean eating, boolean bow, boolean block, float useLeft, float useDuration) {

        static final Use NOTHING = new Use(false, false, false, false, 0.0F, 0.0F);

        static final Use BLOCKING = new Use(true, false, false, true, 0.0F, 0.0F);
    }

    /** 1.7.10 renderItemInFirstPerson for the right hand, up to the item ({@code renderItem}) */
    private static Matrix4f legacyHand(float swing, float equip, Use use, boolean turnsAround) {
        Matrix4f m = new Matrix4f();
        if (use.eating) {
            float left = use.useLeft;
            float used = 1.0F - left / use.useDuration;
            float raised = 1.0F - (float) Math.pow(1.0F - used, 27);
            m.translate(0.0F, Mth.abs(Mth.cos(left / 4.0F * Mth.PI) * 0.1F) * (used > 0.2F ? 1 : 0), 0.0F);
            m.translate(raised * 0.6F, -raised * 0.5F, 0.0F);
            rotate(m, raised * 90.0F, 0, 1, 0);
            rotate(m, raised * 10.0F, 1, 0, 0);
            rotate(m, raised * 30.0F, 0, 0, 1);
        } else if (!use.using) {
            m.translate(-Mth.sin(Mth.sqrt(swing) * Mth.PI) * 0.4F, Mth.sin(Mth.sqrt(swing) * Mth.PI * 2.0F) * 0.2F,
                -Mth.sin(swing * Mth.PI) * 0.2F);
        }
        m.translate(0.7F * 0.8F, -0.65F * 0.8F - equip * 0.6F, -0.9F * 0.8F);
        rotate(m, 45.0F, 0, 1, 0);
        rotate(m, -Mth.sin(swing * swing * Mth.PI) * 20.0F, 0, 1, 0);
        rotate(m, -Mth.sin(Mth.sqrt(swing) * Mth.PI) * 20.0F, 0, 0, 1);
        rotate(m, -Mth.sin(Mth.sqrt(swing) * Mth.PI) * 80.0F, 1, 0, 0);
        m.scale(0.4F);
        if (use.block) {
            m.translate(-0.5F, 0.2F, 0.0F);
            rotate(m, 30.0F, 0, 1, 0);
            rotate(m, -80.0F, 1, 0, 0);
            rotate(m, 60.0F, 0, 1, 0);
        }
        if (use.bow) {
            rotate(m, -18.0F, 0, 0, 1);
            rotate(m, -12.0F, 0, 1, 0);
            rotate(m, -8.0F, 1, 0, 0);
            m.translate(-0.9F, 0.2F, 0.0F);
            float ticks = use.useDuration - use.useLeft;
            float pull = Math.min((ticks / 20.0F * (ticks / 20.0F) + ticks / 20.0F * 2.0F) / 3.0F, 1.0F);
            if (pull > 0.1F) {
                m.translate(0.0F, Mth.sin((ticks - 0.1F) * 1.3F) * 0.01F * (pull - 0.1F), 0.0F);
            }
            m.translate(0.0F, 0.0F, pull * 0.1F);
            rotate(m, -335.0F, 0, 0, 1);
            rotate(m, -50.0F, 0, 1, 0);
            m.translate(0.0F, 0.5F, 0.0F);
            m.scale(1.0F, 1.0F, 1.0F + pull * 0.2F);
            m.translate(0.0F, -0.5F, 0.0F);
            rotate(m, 50.0F, 0, 1, 0);
            rotate(m, 335.0F, 0, 0, 1);
        }
        if (turnsAround) {
            rotate(m, 180.0F, 0, 1, 0);
        }
        return m;
    }

    /** 1.7.10 renderBlockAsItem for a plain block: the matrix the cube around its center is drawn with */
    private static Matrix4f legacyBlock(Matrix4f hand) {
        return rotate(new Matrix4f(hand), 90.0F, 0, 1, 0);
    }

    /** The sprite's corners for the right hand */
    private static List<Vector3f> flatCorners(Matrix4f hand) {
        return ItemDrawingChecks.flatCorners(hand, 1);
    }

    /** The placement of the left hand: the right hand's mirrored across x */
    private static Matrix4f mirrored(Matrix4f hand) {
        return new Matrix4f().scale(-1.0F, 1.0F, 1.0F).mul(hand).scale(-1.0F, 1.0F, 1.0F);
    }

    // ---- comparisons ----------------------------------------------------------------------------------------------

    // ---- tests ----------------------------------------------------------------------------------------------------

    private static void flatItems(TestContext t) {
        Matrix4f hand = legacyHand(0.0F, 0.0F, Use.NOTHING, false);
        for (ItemStack stack : List.of(new ItemStack(Items.APPLE), new ItemStack(Items.DIAMOND_SWORD))) {
            String name = stack.getHoverName().getString();
            hold(t, stack);
            List<Vertex> drawn = draw(t);
            checkAt(t, "on, " + name, drawn, flatCorners(hand));
            t.check("on, " + name + ": sprite mirrored left to right like 1.7.10", spriteLikeLegacy(drawn, hand, 1))
                .isTrue();
        }
        config().legacyFirstPersonItems = false;
        t.check("off, Diamond Sword: farthest 1.7.10 corner (somewhere else, 1.21.1)",
            farthestCorner(draw(t), flatCorners(hand))).atLeast(ELSEWHERE);
    }

    private static void block(TestContext t) {
        Matrix4f hand = legacyHand(0.0F, 0.0F, Use.NOTHING, false);
        hold(t, new ItemStack(Items.COBBLESTONE));
        checkAt(t, "on, Cobblestone", draw(t), blockCorners(legacyBlock(hand)));
        hold(t, new ItemStack(Items.FURNACE));
        List<Vertex> drawn = draw(t);
        checkAt(t, "on, Furnace", drawn, blockCorners(legacyBlock(hand)));
        TextureAtlasSprite front = t.onClientThread(() -> t.mc().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS)
            .getSprite(ResourceLocation.withDefaultNamespace("block/furnace_front")));
        List<Vertex> frontFace = drawn.stream().filter(v -> v.u() >= front.getU0() && v.u() <= front.getU1()
            && v.v() >= front.getV0() && v.v() <= front.getV1()).toList();
        t.require(!frontFace.isEmpty(), "The furnace front was not drawn");
        // 1.7.10 drew the furnace item with its front to the south (+z), then turned the block 90°
        Vector3f expected = legacyBlock(hand).transformDirection(0.0F, 0.0F, 1.0F, new Vector3f()).normalize();
        Vector3f facing = new Vector3f(frontFace.get(0).normal()).normalize();
        t.check("on, Furnace: front faces like 1.7.10 (cosine of the angle)", (double) facing.dot(expected))
            .atLeast(0.999);
    }

    private static void swingAndEquip(TestContext t) {
        hold(t, new ItemStack(Items.DIAMOND_PICKAXE));
        for (float swing : new float[] {0.25F, 0.6F}) {
            checkAt(t, "on, swing " + swing, draw(t, InteractionHand.MAIN_HAND, swing, 0.0F),
                flatCorners(legacyHand(swing, 0.0F, Use.NOTHING, false)));
        }
        checkAt(t, "on, lowered halfway", draw(t, InteractionHand.MAIN_HAND, 0.0F, 0.5F),
            flatCorners(legacyHand(0.0F, 0.5F, Use.NOTHING, false)));
    }

    private static void fishingRod(TestContext t) {
        hold(t, new ItemStack(Items.FISHING_ROD));
        checkAt(t, "on, Fishing Rod", draw(t), flatCorners(legacyHand(0.0F, 0.0F, Use.NOTHING, true)));
        hold(t, new ItemStack(Items.CARROT_ON_A_STICK));
        checkAt(t, "on, Carrot on a Stick", draw(t), flatCorners(legacyHand(0.0F, 0.0F, Use.NOTHING, true)));
    }

    /** A drawing of the hand while an item is used, and what the hand was doing then */
    private record UsedDrawing(List<Vertex> drawn, Use use) {}

    /** Holds use with an item for some ticks, then draws the hand */
    private static UsedDrawing useFor(TestContext t, ItemStack stack, int ticks, boolean bow) {
        hold(t, stack);
        t.hold(t.mc().options.keyUse);
        t.ticks(ticks);
        Use use = t.onClientThread(() -> {
            LocalPlayer player = t.player();
            t.require(player.isUsingItem(), "The item is not being used");
            // drawn at partial tick 1: the use time left is the remaining ticks
            return new Use(true, !bow, bow, false, player.getUseItemRemainingTicks(),
                player.getUseItem().getUseDuration(player));
        });
        List<Vertex> drawn = draw(t);
        t.release(t.mc().options.keyUse);
        t.ticks(5);
        return new UsedDrawing(drawn, use);
    }

    private static void useAnimations(TestContext t) {
        for (int ticks : new int[] {4, 30}) {
            UsedDrawing bow = useFor(t, new ItemStack(Items.BOW), ticks, true);
            checkAt(t, "on, bow drawn " + ticks + " ticks", bow.drawn(),
                flatCorners(legacyHand(0.0F, 0.0F, bow.use(), false)));
        }
        for (int ticks : new int[] {3, 20}) {
            UsedDrawing eating = useFor(t, new ItemStack(Items.COOKED_BEEF), ticks, false);
            checkAt(t, "on, eating " + ticks + " ticks", eating.drawn(),
                flatCorners(legacyHand(0.0F, 0.0F, eating.use(), false)));
        }
    }

    private static void swordBlocking(TestContext t) {
        // sword blocking is a Legacy Mechanics setting: on for this test, then as it was
        LegacyMechanicsConfig mechanics = LegacyMechanicsFeature.getConfig();
        boolean blocking = mechanics.legacySwordBlocking;
        mechanics.legacySwordBlocking = true;
        try {
            hold(t, new ItemStack(Items.DIAMOND_SWORD));
            t.ticks(3);
            t.hold(t.mc().options.keyUse);
            t.ticks(8);
            t.require(t.onClientThread(() -> t.player().isUsingItem()), "The sword is not blocking");
            checkAt(t, "on, blocking", draw(t), flatCorners(legacyHand(0.0F, 0.0F, Use.BLOCKING, false)));
            checkAt(t, "on, blocking while swinging 0.4", draw(t, InteractionHand.MAIN_HAND, 0.4F, 0.0F),
                flatCorners(legacyHand(0.4F, 0.0F, Use.BLOCKING, false)));
            t.release(t.mc().options.keyUse);
            t.ticks(5);
        } finally {
            mechanics.legacySwordBlocking = blocking;
        }
    }

    private static void holdOffHand(TestContext t, ItemStack stack) {
        t.holdItem(ItemStack.EMPTY);
        t.withServerPlayer(player -> player.setItemInHand(InteractionHand.OFF_HAND, stack.copy()));
        t.ticks(3);
    }

    /** Checks the left hand: the mirror image of the right hand's 1.7.10 placement, texture included */
    private static void checkLeft(TestContext t, String name, List<Vertex> drawn, Matrix4f hand) {
        checkAt(t, name, drawn, ItemDrawingChecks.flatCorners(mirrored(hand), -1));
        t.check(name + ": the texture is the mirror image of the right hand's",
            spriteLikeLegacy(drawn, mirrored(hand), -1)).isTrue();
    }

    private static void leftHand(TestContext t) {
        for (ItemStack stack : List.of(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.APPLE),
            new ItemStack(Items.BOW))) {
            holdOffHand(t, stack);
            String name = "on, off hand (left), " + stack.getHoverName().getString();
            checkLeft(t, name, draw(t, InteractionHand.OFF_HAND, 0.0F, 0.0F),
                legacyHand(0.0F, 0.0F, Use.NOTHING, false));
            checkLeft(t, name + ", swing 0.6", draw(t, InteractionHand.OFF_HAND, 0.6F, 0.0F),
                legacyHand(0.6F, 0.0F, Use.NOTHING, false));
        }
        holdOffHand(t, new ItemStack(Items.FISHING_ROD));
        checkLeft(t, "on, off hand (left), Fishing Rod", draw(t, InteractionHand.OFF_HAND, 0.0F, 0.0F),
            legacyHand(0.0F, 0.0F, Use.NOTHING, true));
        for (boolean bow : new boolean[] {true, false}) {
            holdOffHand(t, new ItemStack(bow ? Items.BOW : Items.COOKED_BEEF));
            t.hold(t.mc().options.keyUse);
            t.ticks(bow ? 30 : 12);
            Use use = t.onClientThread(() -> {
                LocalPlayer player = t.player();
                t.require(player.isUsingItem() && player.getUsedItemHand() == InteractionHand.OFF_HAND,
                    "The off-hand item is not being used");
                return new Use(true, !bow, bow, false, player.getUseItemRemainingTicks(),
                    player.getUseItem().getUseDuration(player));
            });
            checkLeft(t, bow ? "on, off hand (left), bow drawn" : "on, off hand (left), eating",
                draw(t, InteractionHand.OFF_HAND, 0.0F, 0.0F), legacyHand(0.0F, 0.0F, use, false));
            t.release(t.mc().options.keyUse);
            t.ticks(5);
        }
    }

    private static void newerItems(TestContext t) {
        for (ItemStack stack : List.of(new ItemStack(Items.SHIELD), new ItemStack(Items.TRIDENT),
            new ItemStack(Items.CROSSBOW))) {
            hold(t, stack);
            List<Vertex> on = draw(t);
            config().legacyFirstPersonItems = false;
            List<Vertex> off = draw(t);
            config().legacyFirstPersonItems = true;
            t.check(stack.getHoverName().getString() + ": drawn the same on and off", samePositions(on, off)).isTrue();
        }
    }

    /** Share of pixels, in percent, that must differ between the 1.7.10 and the 1.21.1 view of an item */
    private static final double VIEWS_DIFFER_PERCENT = 0.5;

    /**
     * Captures the view with 1.7.10 items on and off, saves both and checks they differ (with the HUD: hiding it
     * hides the hand too, in 1.7.10 as well)
     */
    private static void compareViews(TestContext t, String name) {
        config().legacyFirstPersonItems = true;
        try (NativeImage on = t.captureFrame()) {
            config().legacyFirstPersonItems = false;
            try (NativeImage off = t.captureFrame()) {
                t.saveFrame("fps_" + name + "_on", on);
                t.saveFrame("fps_" + name + "_off", off);
                t.check(name + ": differing pixels between on and off, %", Images.differingPercent(on, off))
                    .atLeast(VIEWS_DIFFER_PERCENT);
            }
        } finally {
            config().legacyFirstPersonItems = true;
        }
    }

    /** Holds use for some ticks, then compares the views while still using */
    private static void compareUsing(TestContext t, ItemStack stack, int ticks, String name) {
        hold(t, stack);
        t.hold(t.mc().options.keyUse);
        t.ticks(ticks);
        compareViews(t, name);
        t.release(t.mc().options.keyUse);
        t.ticks(5);
    }

    private static void screenshots(TestContext t) {
        t.teleport(4.5, 1.0, 4.5, 0.0F, 0.0F);
        t.setFlying(true);
        for (ItemStack stack : List.of(new ItemStack(Items.DIAMOND_SWORD), new ItemStack(Items.DIAMOND_PICKAXE),
            new ItemStack(Items.COBBLESTONE), new ItemStack(Items.APPLE), new ItemStack(Items.TORCH),
            new ItemStack(Items.FISHING_ROD))) {
            hold(t, stack);
            t.ticks(10);
            compareViews(t, BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath());
        }
        compareUsing(t, new ItemStack(Items.BOW), 30, "bow_drawn");
        compareUsing(t, new ItemStack(Items.COOKED_BEEF), 16, "eating");
    }

    private static void resourcePackItems(TestContext t) throws Exception {
        Matrix4f hand = legacyHand(0.0F, 0.0F, Use.NOTHING, false);
        TestResourcePack pack = t.resourcePack("item_models")
            .text("assets/minecraft/models/item/apple.json", "{\"parent\":\"minecraft:item/generated\","
                + "\"textures\":{\"layer0\":\"minecraft:item/apple\"}}")
            .text("assets/minecraft/models/item/handheld.json", "{\"parent\":\"minecraft:item/generated\","
                + "\"display\":{\"firstperson_righthand\":{\"rotation\":[0,-90,25],\"translation\":[1,3,1],"
                + "\"scale\":[0.5,0.5,0.5]}}}");
        pack.enable();
        for (ItemStack stack : List.of(new ItemStack(Items.APPLE), new ItemStack(Items.DIAMOND_SWORD))) {
            String name = stack.getHoverName().getString();
            hold(t, stack);
            List<Vertex> kept = draw(t);
            config().legacyFirstPersonItems = false;
            t.check("pack on, " + name + ": drawn as without 1.7.10 items (the pack's look)",
                samePositions(kept, draw(t))).isTrue();
            config().legacyFirstPersonItems = true;
            config().resourcePackItems = false;
            checkAt(t, "pack on, Resource Pack Items off, " + name, draw(t), flatCorners(hand));
            config().resourcePackItems = true;
        }
        hold(t, new ItemStack(Items.CARROT));
        checkAt(t, "pack on, Carrot (model not changed)", draw(t), flatCorners(hand));
    }
}

package io.github.firstone.framework.devtest.features.animatium.hand;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.firstone.framework.devtest.core.Images;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.core.TestResourcePack;
import io.github.firstone.framework.devtest.core.VertexCapture;
import io.github.firstone.framework.devtest.core.VertexCapture.Vertex;
import io.github.firstone.framework.devtest.mixin.ItemInHandRendererInvoker;
import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
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

import java.util.ArrayList;
import java.util.List;

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

    /** Largest distance between an expected and a drawn position (floating point only) */
    private static final float POSITION_TOLERANCE = 1.0E-4F;

    /** A drawing counts as somewhere else when a corner is at least this far from every drawn vertex */
    private static final float ELSEWHERE = 0.01F;

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
    private record Use(boolean using, boolean eating, boolean bow, float useLeft, float useDuration) {

        static final Use NOTHING = new Use(false, false, false, 0.0F, 0.0F);
    }

    private static Matrix4f rotate(Matrix4f m, float degrees, float x, float y, float z) {
        return m.rotate((float) Math.toRadians(degrees), x, y, z);
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

    /** 1.7.10 renderItem for a flat item: the matrix the sprite (0..1, 0..1, -1/16..0) is drawn with */
    private static Matrix4f legacyFlat(Matrix4f hand) {
        Matrix4f m = new Matrix4f(hand);
        m.translate(0.0F, -0.3F, 0.0F);
        m.scale(1.5F);
        rotate(m, 50.0F, 0, 1, 0);
        rotate(m, 335.0F, 0, 0, 1);
        m.translate(-0.9375F, -0.0625F, 0.0F);
        return m;
    }

    /** 1.7.10 renderBlockAsItem for a plain block: the matrix the cube around its center is drawn with */
    private static Matrix4f legacyBlock(Matrix4f hand) {
        return rotate(new Matrix4f(hand), 90.0F, 0, 1, 0);
    }

    private static List<Vector3f> corners(Matrix4f m, float x0, float x1, float y0, float y1, float z0, float z1) {
        List<Vector3f> corners = new ArrayList<>();
        for (float x : new float[] {x0, x1}) {
            for (float y : new float[] {y0, y1}) {
                for (float z : new float[] {z0, z1}) {
                    corners.add(m.transformPosition(x, y, z, new Vector3f()));
                }
            }
        }
        return corners;
    }

    private static List<Vector3f> flatCorners(Matrix4f hand) {
        return corners(legacyFlat(hand), 0.0F, 1.0F, 0.0F, 1.0F, -1.0F / 16.0F, 0.0F);
    }

    private static List<Vector3f> blockCorners(Matrix4f hand) {
        return corners(legacyBlock(hand), -0.5F, 0.5F, -0.5F, 0.5F, -0.5F, 0.5F);
    }

    private static List<Vector3f> mirrored(List<Vector3f> points) {
        List<Vector3f> mirrored = new ArrayList<>();
        for (Vector3f p : points) {
            mirrored.add(new Vector3f(-p.x, p.y, p.z));
        }
        return mirrored;
    }

    // ---- comparisons ----------------------------------------------------------------------------------------------

    /** Largest distance from an expected point to the nearest drawn vertex */
    private static double farthestCorner(List<Vertex> drawn, List<Vector3f> expected) {
        double farthest = 0.0;
        for (Vector3f corner : expected) {
            double nearest = Double.MAX_VALUE;
            for (Vertex vertex : drawn) {
                nearest = Math.min(nearest, vertex.position().distance(corner));
            }
            farthest = Math.max(farthest, nearest);
        }
        return farthest;
    }

    /** Largest distance of a drawn vertex outside the box around the expected points */
    private static double outsideBox(List<Vertex> drawn, List<Vector3f> expected) {
        Vector3f min = new Vector3f(Float.MAX_VALUE);
        Vector3f max = new Vector3f(-Float.MAX_VALUE);
        expected.forEach(p -> {
            min.min(p);
            max.max(p);
        });
        double outside = 0.0;
        for (Vertex vertex : drawn) {
            Vector3f p = vertex.position();
            outside = Math.max(outside, Math.max(Math.max(min.x - p.x, p.x - max.x),
                Math.max(Math.max(min.y - p.y, p.y - max.y), Math.max(min.z - p.z, p.z - max.z))));
        }
        return outside;
    }

    private static void checkAt(TestContext t, String name, List<Vertex> drawn, List<Vector3f> expected) {
        t.require(!drawn.isEmpty(), "Nothing was drawn for: " + name);
        t.check(name + ": farthest 1.7.10 corner from the drawing", farthestCorner(drawn, expected))
            .atMost(POSITION_TOLERANCE);
        t.check(name + ": drawn outside the 1.7.10 shape", outsideBox(drawn, expected)).atMost(POSITION_TOLERANCE);
    }

    private static boolean samePositions(List<Vertex> a, List<Vertex> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i).position().distance(b.get(i).position()) > POSITION_TOLERANCE) {
                return false;
            }
        }
        return true;
    }

    // ---- tests ----------------------------------------------------------------------------------------------------

    private static void flatItems(TestContext t) {
        Matrix4f hand = legacyHand(0.0F, 0.0F, Use.NOTHING, false);
        for (ItemStack stack : List.of(new ItemStack(Items.APPLE), new ItemStack(Items.DIAMOND_SWORD))) {
            String name = stack.getHoverName().getString();
            hold(t, stack);
            List<Vertex> drawn = draw(t);
            checkAt(t, "on, " + name, drawn, flatCorners(hand));
            // 1.7.10 drew the sprite's right edge (u max) at x = 0 and its bottom (v max) at y = 0; only the big front
            // and back faces count (the edge strips reach a little further into the texture)
            Vector3f origin = legacyFlat(hand).transformPosition(0.0F, 0.0F, 0.0F, new Vector3f());
            Vector3f faceNormal = legacyFlat(hand).transformDirection(0.0F, 0.0F, 1.0F, new Vector3f()).normalize();
            List<Vertex> faces = drawn.stream()
                .filter(v -> Math.abs(new Vector3f(v.normal()).normalize().dot(faceNormal)) > 0.99F).toList();
            float maxU = (float) faces.stream().mapToDouble(vertex -> vertex.u()).max().orElse(0.0);
            float maxV = (float) faces.stream().mapToDouble(vertex -> vertex.v()).max().orElse(0.0);
            boolean mirrored = faces.stream().anyMatch(v -> v.position().distance(origin) < POSITION_TOLERANCE
                && Math.abs(v.u() - maxU) < 1.0E-6F && Math.abs(v.v() - maxV) < 1.0E-6F);
            t.check("on, " + name + ": sprite mirrored left to right like 1.7.10", mirrored).isTrue();
        }
        config().legacyFirstPersonItems = false;
        t.check("off, Diamond Sword: farthest 1.7.10 corner (somewhere else, 1.21.1)",
            farthestCorner(draw(t), flatCorners(hand))).atLeast(ELSEWHERE);
    }

    private static void block(TestContext t) {
        Matrix4f hand = legacyHand(0.0F, 0.0F, Use.NOTHING, false);
        hold(t, new ItemStack(Items.COBBLESTONE));
        checkAt(t, "on, Cobblestone", draw(t), blockCorners(hand));
        hold(t, new ItemStack(Items.FURNACE));
        List<Vertex> drawn = draw(t);
        checkAt(t, "on, Furnace", drawn, blockCorners(hand));
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
            return new Use(true, !bow, bow, player.getUseItemRemainingTicks(),
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

    private static void leftHand(TestContext t) {
        t.holdItem(ItemStack.EMPTY);
        t.withServerPlayer(player -> player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.APPLE)));
        t.ticks(3);
        checkAt(t, "on, off hand (left)", draw(t, InteractionHand.OFF_HAND, 0.0F, 0.0F),
            mirrored(flatCorners(legacyHand(0.0F, 0.0F, Use.NOTHING, false))));
        checkAt(t, "on, off hand (left), swing 0.6", draw(t, InteractionHand.OFF_HAND, 0.6F, 0.0F),
            mirrored(flatCorners(legacyHand(0.6F, 0.0F, Use.NOTHING, false))));
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

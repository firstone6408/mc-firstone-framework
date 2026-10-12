package io.github.firstone.framework.devtest.features.animatium.item;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import io.github.firstone.framework.devtest.core.Images;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.core.TestResourcePack;
import io.github.firstone.framework.devtest.core.TestSite;
import io.github.firstone.framework.devtest.core.VertexCapture;
import io.github.firstone.framework.devtest.core.VertexCapture.Vertex;
import io.github.firstone.framework.features.animatium.AnimatiumConfig;
import io.github.firstone.framework.features.animatium.AnimatiumFeature;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.enchantment.Enchantments;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.POSITION_TOLERANCE;
import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.checkAt;
import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.corners;
import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.farthestCorner;
import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.outsideBox;
import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.rotate;
import static io.github.firstone.framework.devtest.features.animatium.item.ItemDrawingChecks.samePositions;

/**
 * Tests of the 1.7.10 items on the ground, part of the Animatium tests
 *
 * <p>An item is spawned, then its renderer draws it once into a {@link VertexCapture}. The vertices are compared with
 * the 1.7.10 drawing written again here from {@code RenderItem.doRender} and {@code renderDroppedItem}: from the
 * middle of the item (0.125 above its bottom) raised by the bob; a block turned by the spin, scaled 0.25, copies
 * scattered by a {@code Random} seeded with 187; a flat item scaled 0.5 and, with Fancy graphics, turned by the spin
 * with its sprites one behind the other, or with Fast graphics a picture turned to the camera.</p>
 */
public final class GroundItemTests {

    /** Distance between the sprites of a Fancy stack (1.7.10: 0.0625 + 0.021875) */
    private static final float SPRITE_SPACING = 0.084375F;

    /** Vertices of one stone block (6 faces) */
    private static final int BLOCK_VERTICES = 24;

    /** Share of pixels, in percent, around the items that must differ between on and off */
    private static final double VIEWS_DIFFER_PERCENT = 0.2;

    /** Where in the world the site's coordinates start */
    private static final BlockPos ORIGIN = TestSite.ORIGIN;

    private GroundItemTests() {}

    /**
     * Returns the ground item tests
     *
     * @return the tests, in the order they run
     */
    public static List<TestCase> tests() {
        return List.of(
            TestCase.of("Ground Items: a block spins a quarter size around the item's middle, front like 1.7.10; off: "
                + "0.125 higher", GroundItemTests::block),
            TestCase.of("Ground Items: block stacks show 1.7.10 copies (2/3/4/5 above 1/5/20/40) scattered like "
                + "1.7.10; off: 1.21.1 copies", GroundItemTests::blockCopies),
            TestCase.of("Ground Items: Fancy flat items spin, sprites one behind the other (1/2/3/4 from 2/16/32), "
                + "with glint", GroundItemTests::fancyFlat),
            TestCase.of("Ground Items: Fast flat items are one picture turned to the camera, not spinning, lit from "
                + "above, no glint", GroundItemTests::fastFlat),
            TestCase.of("Ground Items: Fast stacks show 1.7.10 copies scattered; tinted layers keep their colors",
                GroundItemTests::fastCopiesAndColors),
            TestCase.of("Ground Items: items 1.7.10 did not have and items drawn by code keep their 1.21.1 look",
                GroundItemTests::newerItems),
            TestCase.of("Ground Items: an item whose model a pack changes keeps the pack's look",
                GroundItemTests::resourcePackItems),
            TestCase.of("Ground Items: a named item still shows its name (on and off)", GroundItemTests::names),
            TestCase.slow("Ground Items: screenshots with Fancy and Fast graphics, on and off (to compare with 1.7.10)",
                GroundItemTests::screenshots)
        );
    }

    private static AnimatiumConfig config() {
        return AnimatiumFeature.getConfig();
    }

    // ---- spawning and drawing -------------------------------------------------------------------------------------

    /** Spawns an item that stays (no pickup, no despawn, no motion) and waits until the client has it */
    private static int spawn(TestContext t, ItemStack stack, double x, double z) {
        int id = t.onServer(level -> {
            ItemEntity item = new ItemEntity(level, ORIGIN.getX() + x, ORIGIN.getY(), ORIGIN.getZ() + z, stack.copy(),
                0.0, 0.0, 0.0);
            item.setNeverPickUp();
            item.setUnlimitedLifetime();
            level.addFreshEntity(item);
            return item.getId();
        });
        t.waitUntil("the client sees the item", () -> t.level().getEntity(id) != null, 40);
        t.ticks(2);
        return id;
    }

    /** One drawing of an item, with the bob, spin and camera yaw of that moment */
    private record Drawn(List<Vertex> vertices, List<String> types, float bob, float spin, float cameraYaw) {}

    private static Drawn draw(TestContext t, int id, float partialTick) {
        return t.onClientThread(() -> drawNow(t, id, partialTick));
    }

    /** Draws an item once on the client thread */
    private static Drawn drawNow(TestContext t, int id, float partialTick) {
        Entity entity = t.level().getEntity(id);
        t.require(entity instanceof ItemEntity, "The item is gone");
        ItemEntity item = (ItemEntity) entity;
        EntityRenderer<? super ItemEntity> renderer = t.mc().getEntityRenderDispatcher().getRenderer(item);
        VertexCapture capture = new VertexCapture();
        renderer.render(item, item.getYRot(), partialTick, new PoseStack(), capture, LightTexture.FULL_BRIGHT);
        float age = item.getAge() + partialTick;
        // 1.7.10 doRender: hover and spin
        float bob = Mth.sin(age / 10.0F + item.bobOffs) * 0.1F + 0.1F;
        float spin = (age / 20.0F + item.bobOffs) * (180.0F / (float) Math.PI);
        return new Drawn(capture.vertices(), capture.renderTypes(), bob, spin,
            t.mc().getEntityRenderDispatcher().camera.getYRot());
    }

    /** Draws an item with the option on and off at the same moment */
    private static List<List<Vertex>> drawOnAndOff(TestContext t, int id) {
        return t.onClientThread(() -> {
            config().legacyGroundItems = true;
            List<Vertex> on = drawNow(t, id, 0.5F).vertices();
            config().legacyGroundItems = false;
            List<Vertex> off = drawNow(t, id, 0.5F).vertices();
            config().legacyGroundItems = true;
            return List.of(on, off);
        });
    }

    /** Runs a part of a test with a graphics setting, then puts the setting back */
    private static void withGraphics(TestContext t, GraphicsStatus graphics, Runnable part) {
        GraphicsStatus before = t.onClientThread(() -> t.mc().options.graphicsMode().get());
        setGraphics(t, graphics);
        try {
            part.run();
        } finally {
            setGraphics(t, before);
        }
    }

    private static void setGraphics(TestContext t, GraphicsStatus graphics) {
        t.onClientThread(() -> {
            t.mc().options.graphicsMode().set(graphics);
            return null;
        });
        t.ticks(2);
    }

    /** The vertices of each copy, when every copy has the same number of vertices */
    private static List<List<Vertex>> copies(List<Vertex> drawn, int copies) {
        List<List<Vertex>> groups = new ArrayList<>();
        int each = drawn.size() / copies;
        for (int i = 0; i < copies; i++) {
            groups.add(drawn.subList(i * each, (i + 1) * each));
        }
        return groups;
    }

    /** The 1.7.10 starting point: the item's middle raised by the bob */
    private static Matrix4f middle(Drawn drawn) {
        return new Matrix4f().translate(0.0F, 0.125F + drawn.bob, 0.0F);
    }

    /** The middle of a group of vertices */
    private static Vector3f center(List<Vertex> vertices) {
        Vector3f sum = new Vector3f();
        vertices.forEach(vertex -> sum.add(vertex.position()));
        return sum.div(vertices.size());
    }

    /** 1.7.10 copy counts */
    private static int legacyCopies(int count) {
        return count > 40 ? 5 : count > 20 ? 4 : count > 5 ? 3 : count > 1 ? 2 : 1;
    }

    private static int legacySprites(int count) {
        return count < 2 ? 1 : count < 16 ? 2 : count < 32 ? 3 : 4;
    }

    /** 1.21.1 copy counts ({@code ItemEntityRenderer.getRenderedAmount}) */
    private static int vanillaCopies(int count) {
        return count <= 1 ? 1 : count <= 16 ? 2 : count <= 32 ? 3 : count <= 48 ? 4 : 5;
    }

    // ---- blocks ---------------------------------------------------------------------------------------------------

    private static Matrix4f blockFrame(Drawn drawn) {
        Matrix4f m = middle(drawn);
        rotate(m, drawn.spin, 0, 1, 0);
        return m.scale(0.25F);
    }

    private static void block(TestContext t) {
        int stone = spawn(t, new ItemStack(Items.STONE), 4.5, 4.5);
        Drawn on = draw(t, stone, 0.5F);
        checkAt(t, "on, Stone", on.vertices, corners(blockFrame(on), -0.5F, 0.5F, -0.5F, 0.5F, -0.5F, 0.5F));
        t.check("on, Stone: middle above the item's bottom (1.7.10: 0.125 + bob)", center(on.vertices).y - on.bob)
            .near(0.125, POSITION_TOLERANCE);

        // the front of a furnace: south in the turned frame (1.7.10 renderBlockAsItem)
        int furnace = spawn(t, new ItemStack(Items.FURNACE), 8.5, 4.5);
        TextureAtlasSprite front = t.onClientThread(() -> t.mc().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS)
            .getSprite(ResourceLocation.withDefaultNamespace("block/furnace_front")));
        Drawn drawn = draw(t, furnace, 0.5F);
        Vector3f south = new Matrix4f().rotateY((float) Math.toRadians(drawn.spin))
            .transformDirection(new Vector3f(0.0F, 0.0F, 1.0F));
        List<Vertex> frontFace = drawn.vertices.stream().filter(v -> v.u() >= front.getU0() && v.u() <= front.getU1()
            && v.v() >= front.getV0() && v.v() <= front.getV1()).toList();
        t.check("on, Furnace: vertices of the front texture", frontFace.size()).near(4, 0);
        t.check("on, Furnace: the front faces south of the turned block (1.7.10)",
            new Vector3f(frontFace.get(0).normal()).normalize().dot(south)).near(1.0, 1.0E-4);

        config().legacyGroundItems = false;
        Drawn off = draw(t, stone, 0.5F);
        t.check("off, Stone: middle above the item's bottom (1.21.1: 0.25 + bob)", center(off.vertices).y - off.bob)
            .near(0.25, POSITION_TOLERANCE);
    }

    private static void blockCopies(TestContext t) {
        int[] counts = {1, 2, 5, 6, 20, 21, 40, 41, 64};
        for (int i = 0; i < counts.length; i++) {
            int id = spawn(t, new ItemStack(Items.STONE, counts[i]), 2.5 + i * 1.5, 4.5);
            List<List<Vertex>> drawn = drawOnAndOff(t, id);
            t.check("on, " + counts[i] + " stone: blocks (1.7.10: " + legacyCopies(counts[i]) + ")",
                drawn.get(0).size() / BLOCK_VERTICES).near(legacyCopies(counts[i]), 0);
            t.check("off, " + counts[i] + " stone: blocks (1.21.1: " + vanillaCopies(counts[i]) + ")",
                drawn.get(1).size() / BLOCK_VERTICES).near(vanillaCopies(counts[i]), 0);
        }
        // where the 5 blocks of a full stack are: java.util.Random seeded with 187, 0.2 / 0.25 in the scaled frame
        int full = spawn(t, new ItemStack(Items.STONE, 64), 4.5, 8.5);
        Drawn drawn = draw(t, full, 0.5F);
        Random random = new Random(187L);
        List<List<Vertex>> blocks = copies(drawn.vertices, 5);
        for (int i = 0; i < 5; i++) {
            Matrix4f m = blockFrame(drawn);
            if (i > 0) {
                float range = 0.2F / 0.25F;
                m.translate((random.nextFloat() * 2.0F - 1.0F) * range, (random.nextFloat() * 2.0F - 1.0F) * range,
                    (random.nextFloat() * 2.0F - 1.0F) * range);
            }
            checkAt(t, "on, 64 stone, block " + (i + 1), blocks.get(i), corners(m, -0.5F, 0.5F, -0.5F, 0.5F, -0.5F,
                0.5F));
        }
    }

    // ---- flat items -----------------------------------------------------------------------------------------------

    /** Where 1.7.10 drew sprite {@code index} of {@code sprites} with Fancy graphics ({@code renderItemIn2D}) */
    private static Matrix4f spriteFrame(Drawn drawn, int index, int sprites) {
        Matrix4f m = middle(drawn).scale(0.5F);
        rotate(m, drawn.spin, 0, 1, 0);
        m.translate(-0.5F, -0.25F, -(SPRITE_SPACING * sprites / 2.0F));
        return m.translate(0.0F, 0.0F, SPRITE_SPACING * (index + 1));
    }

    /** Checks one Fancy sprite: its corners, and its texture mirrored like 1.7.10 (u and v max at (0, 0, 0)) */
    private static void checkSprite(TestContext t, String name, List<Vertex> drawn, Matrix4f sprite) {
        checkAt(t, name, drawn, corners(sprite, 0.0F, 1.0F, 0.0F, 1.0F, -1.0F / 16.0F, 0.0F));
        Vector3f origin = sprite.transformPosition(new Vector3f());
        Vector3f faceNormal = sprite.transformDirection(new Vector3f(0.0F, 0.0F, 1.0F)).normalize();
        List<Vertex> faces = drawn.stream()
            .filter(v -> Math.abs(new Vector3f(v.normal()).normalize().dot(faceNormal)) > 0.99F).toList();
        float maxU = (float) faces.stream().mapToDouble(v -> v.u()).max().orElse(0.0);
        float maxV = (float) faces.stream().mapToDouble(v -> v.v()).max().orElse(0.0);
        t.check(name + ": texture mirrored like 1.7.10 (u and v max at the sprite's corner)",
            faces.stream().anyMatch(v -> v.position().distance(origin) < POSITION_TOLERANCE
                && Math.abs(v.u() - maxU) < 1.0E-6F && Math.abs(v.v() - maxV) < 1.0E-6F)).isTrue();
    }

    private static void fancyFlat(TestContext t) {
        withGraphics(t, GraphicsStatus.FANCY, () -> {
            int diamond = spawn(t, new ItemStack(Items.DIAMOND), 4.5, 4.5);
            Drawn one = draw(t, diamond, 0.5F);
            checkSprite(t, "on, Diamond", one.vertices, spriteFrame(one, 0, 1));
            int[] counts = {1, 2, 15, 16, 31, 32, 64};
            for (int i = 0; i < counts.length; i++) {
                int id = spawn(t, new ItemStack(Items.DIAMOND, counts[i]), 2.5 + i * 1.5, 8.5);
                Drawn drawn = draw(t, id, 0.5F);
                int sprites = legacySprites(counts[i]);
                t.check("on, " + counts[i] + " diamonds: sprites (1.7.10: " + sprites + ")",
                    drawn.vertices.size() / one.vertices.size()).near(sprites, 0);
                List<List<Vertex>> groups = copies(drawn.vertices, sprites);
                for (int s = 0; s < sprites; s++) {
                    checkSprite(t, "on, " + counts[i] + " diamonds, sprite " + (s + 1), groups.get(s),
                        spriteFrame(drawn, s, sprites));
                }
            }
            ItemStack sword = t.enchanted(Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 1);
            int enchanted = spawn(t, sword, 4.5, 12.5);
            t.check("on, Fancy, enchanted sword: glint drawn (1.7.10)",
                draw(t, enchanted, 0.5F).types.stream().anyMatch(type -> type.contains("glint"))).isTrue();

            config().legacyGroundItems = false;
            int full = spawn(t, new ItemStack(Items.DIAMOND, 64), 8.5, 12.5);
            t.check("off, 64 diamonds: sprites (1.21.1: 5)",
                draw(t, full, 0.5F).vertices.size() / one.vertices.size()).near(5, 0);
        });
    }

    /** Where 1.7.10 drew a Fast picture: turned to the camera, after the copy's scatter */
    private static Matrix4f pictureFrame(Drawn drawn, float[] scatter) {
        Matrix4f m = middle(drawn).scale(0.5F).translate(scatter[0], scatter[1], scatter[2]);
        return rotate(m, 180.0F - drawn.cameraYaw, 0, 1, 0);
    }

    /** Checks one Fast picture: corners, texture the right way round, lit from above, turned to the camera */
    private static void checkPicture(TestContext t, String name, List<Vertex> drawn, Drawn at, float[] scatter) {
        Matrix4f frame = pictureFrame(at, scatter);
        List<Vector3f> expected = corners(frame, -0.5F, 0.5F, -0.25F, 0.75F, 0.0F, 0.0F);
        t.check(name + ": farthest 1.7.10 corner from the drawing", farthestCorner(drawn, expected))
            .atMost(POSITION_TOLERANCE);
        t.check(name + ": drawn outside the 1.7.10 picture", outsideBox(drawn, expected)).atMost(POSITION_TOLERANCE);
        Vector3f bottomLeft = frame.transformPosition(new Vector3f(-0.5F, -0.25F, 0.0F));
        float minU = (float) drawn.stream().mapToDouble(v -> v.u()).min().orElse(0.0);
        float maxV = (float) drawn.stream().mapToDouble(v -> v.v()).max().orElse(0.0);
        t.check(name + ": texture the right way round (u min, v max at the bottom left)",
            drawn.stream().anyMatch(v -> v.position().distance(bottomLeft) < POSITION_TOLERANCE
                && Math.abs(v.u() - minU) < 1.0E-6F && Math.abs(v.v() - maxV) < 1.0E-6F)).isTrue();
        t.check(name + ": every normal points up (1.7.10 setNormal(0, 1, 0))", drawn.stream()
            .allMatch(v -> new Vector3f(v.normal()).normalize().distance(0.0F, 1.0F, 0.0F) < 1.0E-4F)).isTrue();
        // the corners go counter-clockwise seen from the camera: the game hides the other side
        Vector3f a = drawn.get(0).position();
        Vector3f winding = new Vector3f(drawn.get(1).position()).sub(a)
            .cross(new Vector3f(drawn.get(2).position()).sub(a));
        float yaw = (float) Math.toRadians(at.cameraYaw);
        Vector3f look = new Vector3f(-Mth.sin(yaw), 0.0F, Mth.cos(yaw));
        t.check(name + ": front turned to the camera", winding.normalize().dot(look)).atMost(-0.99);
    }

    private static void fastFlat(TestContext t) {
        withGraphics(t, GraphicsStatus.FAST, () -> {
            for (float yaw : new float[] {0.0F, 70.0F, -135.0F}) {
                t.teleport(4.5, 0.0, 1.5, yaw, 20.0F);
                t.ticks(2);
                int diamond = spawn(t, new ItemStack(Items.DIAMOND), 4.5, 4.5);
                Drawn drawn = draw(t, diamond, 0.5F);
                t.check("on, Fast, Diamond, camera " + yaw + "°: vertices (one picture)", drawn.vertices.size())
                    .near(4, 0);
                checkPicture(t, "on, Fast, Diamond, camera " + yaw + "°", drawn.vertices, drawn, new float[3]);
                t.removeEntities();
            }
            // no spin: a later moment moves the picture only up or down (the bob)
            int diamond = spawn(t, new ItemStack(Items.DIAMOND), 4.5, 4.5);
            Drawn early = draw(t, diamond, 0.0F);
            t.ticks(7);
            Drawn late = draw(t, diamond, 0.0F);
            double sideways = 0.0;
            for (int i = 0; i < 4; i++) {
                Vector3f a = early.vertices.get(i).position();
                Vector3f b = late.vertices.get(i).position();
                sideways = Math.max(sideways, Math.hypot(a.x - b.x, a.z - b.z));
            }
            t.check("on, Fast: does not spin (no sideways move over 7 ticks)", sideways).atMost(POSITION_TOLERANCE);

            ItemStack sword = t.enchanted(Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 1);
            int enchanted = spawn(t, sword, 8.5, 4.5);
            Drawn swordDrawn = draw(t, enchanted, 0.5F);
            t.check("on, Fast, enchanted sword: no glint (1.7.10)",
                swordDrawn.types.stream().noneMatch(type -> type.contains("glint"))).isTrue();
            config().legacyGroundItems = false;
            Drawn off = draw(t, enchanted, 0.5F);
            t.check("off, Fast, enchanted sword: glint (1.21.1)",
                off.types.stream().anyMatch(type -> type.contains("glint"))).isTrue();
            t.check("off, Fast, Diamond: a 3D model (1.21.1)", draw(t, diamond, 0.5F).vertices.size()).atLeast(12);
        });
    }

    private static void fastCopiesAndColors(TestContext t) {
        withGraphics(t, GraphicsStatus.FAST, () -> {
            int[] counts = {2, 5, 6, 20, 21, 40, 41};
            for (int i = 0; i < counts.length; i++) {
                int id = spawn(t, new ItemStack(Items.DIAMOND, counts[i]), 2.5 + i * 1.5, 4.5);
                t.check("on, Fast, " + counts[i] + " diamonds: pictures (1.7.10: " + legacyCopies(counts[i]) + ")",
                    draw(t, id, 0.5F).vertices.size() / 4).near(legacyCopies(counts[i]), 0);
            }
            // where the 5 pictures of a full stack are: java.util.Random seeded with 187, 0.3 at half size
            int full = spawn(t, new ItemStack(Items.DIAMOND, 64), 4.5, 8.5);
            Drawn drawn = draw(t, full, 0.5F);
            Random random = new Random(187L);
            List<List<Vertex>> pictures = copies(drawn.vertices, 5);
            for (int i = 0; i < 5; i++) {
                float[] scatter = new float[3];
                if (i > 0) {
                    for (int axis = 0; axis < 3; axis++) {
                        scatter[axis] = (random.nextFloat() * 2.0F - 1.0F) * 0.3F;
                    }
                }
                checkPicture(t, "on, Fast, 64 diamonds, picture " + (i + 1), pictures.get(i), drawn, scatter);
            }
            // a dyed leather helmet: the dyed layer and the white overlay
            ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
            helmet.set(DataComponents.DYED_COLOR, new DyedItemColor(0x336699, true));
            int dyed = spawn(t, helmet, 8.5, 8.5);
            List<Vertex> layers = draw(t, dyed, 0.5F).vertices;
            t.check("on, Fast, dyed helmet: vertices (two layers)", layers.size()).near(8, 0);
            t.check("on, Fast, dyed helmet: dyed layer vertices", layers.stream()
                .filter(v -> v.color() == 0xFF336699).count()).near(4, 0);
            t.check("on, Fast, dyed helmet: white overlay vertices", layers.stream()
                .filter(v -> v.color() == -1).count()).near(4, 0);
        });
    }

    // ---- items that keep the 1.21.1 look --------------------------------------------------------------------------

    private static void newerItems(TestContext t) {
        ItemStack[] stacks = {new ItemStack(Items.SHIELD), new ItemStack(Items.TRIDENT), new ItemStack(Items.CHEST),
            new ItemStack(Items.FILLED_MAP)};
        for (GraphicsStatus graphics : new GraphicsStatus[] {GraphicsStatus.FANCY, GraphicsStatus.FAST}) {
            withGraphics(t, graphics, () -> {
                for (int i = 0; i < stacks.length; i++) {
                    int id = spawn(t, stacks[i], 2.5 + i * 2.0, 4.5);
                    List<List<Vertex>> drawn = drawOnAndOff(t, id);
                    t.check(graphics + ", " + stacks[i].getHoverName().getString() + ": drawn as without 1.7.10 "
                        + "ground items", samePositions(drawn.get(0), drawn.get(1))).isTrue();
                }
                t.removeEntities();
            });
        }
    }

    private static void resourcePackItems(TestContext t) throws Exception {
        TestResourcePack pack = t.resourcePack("diamond_model")
            .text("assets/minecraft/models/item/diamond.json", "{\"parent\":\"minecraft:item/generated\","
                + "\"textures\":{\"layer0\":\"minecraft:item/diamond\"}}");
        pack.enable();
        withGraphics(t, GraphicsStatus.FANCY, () -> {
            int id = spawn(t, new ItemStack(Items.DIAMOND), 4.5, 4.5);
            List<List<Vertex>> drawn = drawOnAndOff(t, id);
            t.check("pack on, Diamond: drawn as without 1.7.10 ground items (the pack's look)",
                samePositions(drawn.get(0), drawn.get(1))).isTrue();
            config().resourcePackItems = false;
            Drawn legacy = draw(t, id, 0.5F);
            checkSprite(t, "pack on, Resource Pack Items off, Diamond", legacy.vertices, spriteFrame(legacy, 0, 1));
            config().resourcePackItems = true;
        });
    }

    private static void names(TestContext t) {
        int id = spawn(t, new ItemStack(Items.STONE), 4.5, 4.5);
        t.onServer(level -> {
            Entity item = level.getEntity(id);
            t.require(item != null, "The item is gone");
            item.setCustomName(Component.literal("Named"));
            item.setCustomNameVisible(true);
            return null;
        });
        t.ticks(3);
        for (boolean on : new boolean[] {true, false}) {
            config().legacyGroundItems = on;
            Drawn drawn = draw(t, id, 0.5F);
            t.check((on ? "on" : "off") + ": name drawn", drawn.types.stream().anyMatch(type -> type.contains("text")))
                .isTrue();
        }
    }

    // ---- screenshots ----------------------------------------------------------------------------------------------

    /** Captures the view with the option on and off, saves both and checks the items differ */
    private static void compareViews(TestContext t, String name) {
        config().legacyGroundItems = true;
        try (NativeImage on = t.captureFrame()) {
            config().legacyGroundItems = false;
            try (NativeImage off = t.captureFrame()) {
                t.saveFrame("ground_" + name + "_on", on);
                t.saveFrame("ground_" + name + "_off", off);
                int width = on.getWidth() / 2;
                int height = on.getHeight() / 2;
                t.check(name + ": differing pixels around the items between on and off, %", Images.differingPercent(
                    on, off, (on.getWidth() - width) / 2, (on.getHeight() - height) / 2, width, height))
                    .atLeast(VIEWS_DIFFER_PERCENT);
            }
        } finally {
            config().legacyGroundItems = true;
        }
    }

    private static void screenshots(TestContext t) {
        t.teleport(4.5, 0.0, 2.0, 0.0F, 30.0F);
        t.setFlying(true);
        ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
        helmet.set(DataComponents.DYED_COLOR, new DyedItemColor(0x336699, true));
        ItemStack[] stacks = {new ItemStack(Items.STONE, 64), new ItemStack(Items.DIAMOND, 64), new ItemStack(
            Items.FURNACE), t.enchanted(Items.DIAMOND_SWORD, Enchantments.SHARPNESS, 1), helmet};
        for (int i = 0; i < stacks.length; i++) {
            spawn(t, stacks[i], 3.3 + i * 0.6, 4.0);
        }
        t.mc().options.hideGui = true;
        withGraphics(t, GraphicsStatus.FANCY, () -> compareViews(t, "fancy"));
        withGraphics(t, GraphicsStatus.FAST, () -> compareViews(t, "fast"));
    }
}

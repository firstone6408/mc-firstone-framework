package io.github.firstone.framework.devtest.features.legacymechanics.item;

import io.github.firstone.framework.devtest.core.Spawned;
import io.github.firstone.framework.devtest.core.TestCase;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.core.TestSite;
import io.github.firstone.framework.devtest.mixin.EyeOfEnderAccessor;
import io.github.firstone.framework.devtest.mixin.FishingHookAccessor;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsConfig;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.EyeOfEnder;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.List;

/**
 * Tests of the 1.7.10 item drops, part of the Legacy Mechanics tests
 *
 * <p>Every drop is made and measured inside one server task: the item is created, ticked once by hand and removed, so
 * the results are exact. With the option on, items appear where 1.7.10 put them ({@code entityDropItem}: the middle
 * of the item at the dropper's position, so 0.125 lower; boats, minecarts and falling blocks had their position in
 * their middle) and an item inside a block is kicked like 1.7.10 {@code Entity.pushOutOfBlocks}: towards the nearest
 * open side in the order west, east, up, north, south, at 0.1–0.3, the other speeds kept. With it off: the 1.21.1
 * height and {@code Entity.moveTowardsClosestSpace} (order north, south, west, east, up; other speeds × 0.75).</p>
 */
public final class ItemDropTests {

    /** How much lower a 1.7.10 item appears: half its height */
    private static final double ITEM_HALF = 0.125;

    /** Gravity of an item per tick (both versions) */
    private static final double GRAVITY = 0.04;

    /** Air friction of an item per tick (both versions) */
    private static final double AIR = 0.98;

    /** Speeds a kick can give, after one tick of air friction (1.7.10 and 1.21.1: 0.1 to 0.3) */
    private static final double MIN_KICK = 0.1 * AIR;
    private static final double MAX_KICK = 0.3 * AIR;

    /** How much of the other speeds 1.21.1 keeps when it kicks an item */
    private static final double VANILLA_KEEP = 0.75;

    /** Allowed difference of values computed the same way (float speeds stored in doubles) */
    private static final double EXACT = 1.0E-6;

    /** How far apart the client's and the server's item may be (a tick apart, nearly stopped) */
    private static final double CLIENT_TOLERANCE = 0.1;

    /** Where in the world the site's coordinates start */
    private static final BlockPos ORIGIN = TestSite.ORIGIN;

    private ItemDropTests() {}

    /**
     * Returns the item drop tests
     *
     * @return the tests, in the order they run
     */
    public static List<TestCase> tests() {
        return List.of(
            TestCase.of("Item drops: an entity's drops appear 0.125 lower, also above its feet; off: 1.21.1 height",
                ItemDropTests::entityDropHeight),
            TestCase.of("Item drops: boats, minecarts and falling blocks drop from their 1.7.10 middle; off: from the "
                + "bottom", ItemDropTests::middleDropHeight),
            TestCase.of("Item drops: items players drop appear 0.125 lower; off: 1.21.1 height",
                ItemDropTests::playerDropHeight),
            TestCase.of("Item drops: item frames, mooshrooms, fishing and eyes of ender drop 0.125 lower; off: 1.21.1 "
                + "height", ItemDropTests::otherDropHeights),
            TestCase.of("Item drops: a drop at a mob's feet is kicked out of the ground to the nearest side, the other "
                + "speeds kept; off: not kicked", ItemDropTests::kickFromGround),
            TestCase.of("Item drops: the 1.7.10 kick keeps the other speeds; off: 1.21.1 slows them to 0.75",
                ItemDropTests::kickKeepsSpeeds),
            TestCase.of("Item drops: a kick is sent to the players at once, also when the speed barely changes",
                ItemDropTests::kickSentAtOnce),
            TestCase.of("Item drops: on a tie the 1.7.10 order wins (west before north); off: north (1.21.1)",
                ItemDropTests::kickTie),
            TestCase.of("Item drops: a full block closes a side, a slab does not (on and off)",
                ItemDropTests::closedSides),
            TestCase.of("Item drops: an item in a block open only above goes up, never down (on and off)",
                ItemDropTests::onlyUp),
            TestCase.of("Item drops: only blocks count, an item inside a boat is not kicked; off: kicked (1.21.1)",
                ItemDropTests::insideBoat),
            TestCase.of("Item drops: the client sees the kick at once and agrees with the server (on and off)",
                ItemDropTests::clientSeesKick)
        );
    }

    private static LegacyMechanicsConfig config() {
        return LegacyMechanicsFeature.getConfig();
    }

    // ---- helpers --------------------------------------------------------------------------------------------------

    /**
     * One dropped item: the height of what dropped it, its own height, and its motion before and after one tick
     *
     * @param impulse whether the item's motion is sent to the players at once ({@code hasImpulse})
     */
    private record Drop(double sourceY, double itemY, Vec3 before, Vec3 after, boolean impulse) {}

    /** Ticks an item once by hand, removes it and returns what happened */
    private static Drop tickOnce(double sourceY, ItemEntity item) {
        double y = item.getY();
        Vec3 before = item.getDeltaMovement();
        item.tick();
        Drop drop = new Drop(sourceY, y, before, item.getDeltaMovement(), item.hasImpulse);
        item.discard();
        return drop;
    }

    /** Creates an entity at a site position, not added to the world (it only drops items) */
    private static Entity create(TestContext t, ServerLevel level, EntityType<?> type, double x, double y, double z) {
        Entity entity = type.create(level);
        t.require(entity != null, "Cannot create " + type);
        entity.moveTo(ORIGIN.getX() + x, ORIGIN.getY() + y, ORIGIN.getZ() + z, 0.0F, 0.0F);
        return entity;
    }

    /** Makes an entity of the type at (x, 0, z) drop leather and ticks the item once */
    private static Drop drop(TestContext t, EntityType<?> type, double x, double z, float above) {
        return t.onServer(level -> {
            Entity source = create(t, level, type, x, 0.0, z);
            ItemEntity item = source.spawnAtLocation(new ItemStack(Items.LEATHER), above);
            t.require(item != null, "No item dropped");
            return tickOnce(source.getY(), item);
        });
    }

    /** A cow at (x, 0, z) of the site drops leather at its feet; the item is ticked once */
    private static Drop cowDrop(TestContext t, double x, double z) {
        return drop(t, EntityType.COW, x, z, 0.0F);
    }

    /** An item with a known motion placed with its bottom at a site position; ticked once */
    private static Drop placed(TestContext t, double x, double y, double z, Vec3 motion) {
        return t.onServer(level -> tickOnce(Double.NaN, new ItemEntity(level, ORIGIN.getX() + x, ORIGIN.getY() + y,
            ORIGIN.getZ() + z, new ItemStack(Items.LEATHER), motion.x, motion.y, motion.z)));
    }

    /** The items within a box around a position, removed after reading their heights */
    private static double[] itemHeightsNear(ServerLevel level, Vec3 at) {
        double[] heights = new double[0];
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(1.0, 2.0, 1.0))) {
            heights = Arrays.copyOf(heights, heights.length + 1);
            heights[heights.length - 1] = item.getY();
            item.discard();
        }
        return heights;
    }

    /** The motion an item has after one tick in the air when nothing kicks it, scaled by {@code keep} */
    private static double fallen(Drop drop, Direction.Axis axis, double keep) {
        double before = drop.before.get(axis) - (axis == Direction.Axis.Y ? GRAVITY : 0.0);
        return before * keep * AIR;
    }

    /** Checks one tick of an item kicked towards {@code side}, its other speeds scaled by {@code keep} */
    private static void checkKick(TestContext t, String name, Drop drop, Direction side, double keep) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            double after = drop.after.get(axis);
            if (axis == side.getAxis()) {
                t.check(name + ": kicked " + side.getName() + " at 0.1-0.3", after * side.getAxisDirection().getStep())
                    .between(MIN_KICK - EXACT, MAX_KICK + EXACT);
            } else {
                t.check(name + ": " + axis.getName() + " speed x " + keep, after).near(fallen(drop, axis, keep), EXACT);
            }
        }
    }

    /** Checks one tick of an item that nothing kicked */
    private static void checkNoKick(TestContext t, String name, Drop drop) {
        for (Direction.Axis axis : Direction.Axis.values()) {
            t.check(name + ": " + axis.getName() + " speed not kicked", drop.after.get(axis))
                .near(fallen(drop, axis, 1.0), EXACT);
        }
        t.check(name + ": motion not sent at once", drop.impulse).isFalse();
    }

    // ---- heights --------------------------------------------------------------------------------------------------

    private static void entityDropHeight(TestContext t) {
        Drop feet = cowDrop(t, 4.5, 4.5);
        t.check("on: cow drop at the feet - 0.125 (1.7.10)", feet.itemY - feet.sourceY).near(-ITEM_HALF, EXACT);
        Drop above = drop(t, EntityType.COW, 4.5, 4.5, 1.0F);
        t.check("on: cow drop 1 above the feet - 0.125 (1.7.10)", above.itemY - above.sourceY)
            .near(1.0 - ITEM_HALF, EXACT);

        config().legacyItemDrops = false;
        feet = cowDrop(t, 4.5, 4.5);
        t.check("off: cow drop at the feet (1.21.1)", feet.itemY - feet.sourceY).near(0.0, EXACT);
        above = drop(t, EntityType.COW, 4.5, 4.5, 1.0F);
        t.check("off: cow drop 1 above the feet (1.21.1)", above.itemY - above.sourceY).near(1.0, EXACT);
    }

    private static void middleDropHeight(TestContext t) {
        Object[][] sources = {
            {"boat", EntityType.BOAT, 0.3}, {"minecart", EntityType.MINECART, 0.35},
            {"falling block", EntityType.FALLING_BLOCK, 0.49}, {"chest boat", EntityType.CHEST_BOAT, 0.3}
        };
        for (boolean on : new boolean[] {true, false}) {
            config().legacyItemDrops = on;
            for (Object[] source : sources) {
                Drop drop = drop(t, (EntityType<?>) source[1], 4.5, 4.5, 0.0F);
                double expected = on ? (double) source[2] - ITEM_HALF : 0.0;
                t.check(String.format("%s: %s drop above its bottom (%s: %.3f)", on ? "on" : "off", source[0],
                    on ? "1.7.10" : "1.21.1", expected), drop.itemY - drop.sourceY).near(expected, EXACT);
            }
        }
    }

    /** The height of an item the player drops, relative to the player's eyes */
    private static double playerDrop(TestContext t) {
        return t.onServer(level -> {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.player().getUUID());
            t.require(player != null, "The server has no player");
            ItemEntity item = player.drop(new ItemStack(Items.LEATHER), false, false);
            t.require(item != null, "No item dropped");
            double y = item.getY() - player.getEyeY();
            item.discard();
            return y;
        });
    }

    private static void playerDropHeight(TestContext t) {
        // 1.21.1 Player.drop: eye height - 0.3F
        t.check("on: eyes - 0.3 - 0.125 (1.7.10)", playerDrop(t)).near(-0.3F - ITEM_HALF, EXACT);
        config().legacyItemDrops = false;
        t.check("off: eyes - 0.3 (1.21.1)", playerDrop(t)).near(-0.3F, EXACT);
    }

    /** Heights of items dropped by an item frame, a sheared mooshroom, a fishing catch and an eye of ender */
    private static double[] otherDrops(TestContext t) {
        t.holdItem(new ItemStack(Items.FISHING_ROD));
        return t.onServer(level -> {
            ItemFrame frame = (ItemFrame) create(t, level, EntityType.ITEM_FRAME, 4.5, 1.5, 4.5);
            ItemEntity frameItem = frame.spawnAtLocation(new ItemStack(Items.ITEM_FRAME), 0.0F);
            t.require(frameItem != null, "The item frame dropped nothing");
            double frameDrop = frameItem.getY() - frame.getY();
            frameItem.discard();

            MushroomCow mooshroom = (MushroomCow) create(t, level, EntityType.MOOSHROOM, 8.5, 0.0, 4.5);
            double top = mooshroom.getY(1.0);
            mooshroom.shear(SoundSource.PLAYERS);
            double[] mushrooms = itemHeightsNear(level, mooshroom.position());
            t.require(mushrooms.length == 5, "The mooshroom dropped " + mushrooms.length + " mushrooms, not 5");
            double lowest = Double.MAX_VALUE;
            double highest = -Double.MAX_VALUE;
            for (double y : mushrooms) {
                lowest = Math.min(lowest, y - top);
                highest = Math.max(highest, y - top);
            }

            ServerPlayer player = level.getServer().getPlayerList().getPlayer(t.player().getUUID());
            t.require(player != null, "The server has no player");
            FishingHook hook = new FishingHook(player, level, 0, 0);
            ((FishingHookAccessor) hook).devtest$setNibble(1);
            double hookY = hook.getY();
            Vec3 hookAt = hook.position();
            t.require(hook.retrieve(player.getMainHandItem()) == 1, "Nothing was caught");
            double[] catches = itemHeightsNear(level, hookAt);
            t.require(catches.length == 1, catches.length + " items were caught, not 1");

            EyeOfEnder eye = new EyeOfEnder(level, ORIGIN.getX() + 12.5, ORIGIN.getY() + 3.0, ORIGIN.getZ() + 4.5);
            ((EyeOfEnderAccessor) eye).devtest$setLife(80);
            ((EyeOfEnderAccessor) eye).devtest$setSurviveAfterDeath(true);
            eye.tick();
            double[] eyes = itemHeightsNear(level, eye.position());
            t.require(eyes.length == 1, "The eye of ender dropped " + eyes.length + " items, not 1");
            return new double[] {frameDrop, lowest, highest, catches[0] - hookY, eyes[0] - eye.getY()};
        });
    }

    private static void otherDropHeights(TestContext t) {
        String[] names = {"item frame drop", "lowest mushroom above the mooshroom's top",
            "highest mushroom above the mooshroom's top", "fishing catch at the hook", "eye of ender drop"};
        double[] on = otherDrops(t);
        for (int i = 0; i < names.length; i++) {
            t.check("on: " + names[i] + " - 0.125 (1.7.10)", on[i]).near(-ITEM_HALF, EXACT);
        }
        t.removeEntities();
        config().legacyItemDrops = false;
        double[] off = otherDrops(t);
        for (int i = 0; i < names.length; i++) {
            t.check("off: " + names[i] + " (1.21.1)", off[i]).near(0.0, EXACT);
        }
    }

    // ---- kicks ----------------------------------------------------------------------------------------------------

    private static void kickFromGround(TestContext t) {
        // where in the block the cow stands (x, z) and the nearest side: 1.7.10 tries west, east, up, north, south
        Object[][] cases = {
            {0.3, 0.6, Direction.WEST}, {0.75, 0.5, Direction.EAST}, {0.7, 0.2, Direction.NORTH},
            {0.8, 0.9, Direction.SOUTH}
        };
        for (Object[] c : cases) {
            Drop drop = cowDrop(t, 4.0 + (double) c[0], 4.0 + (double) c[1]);
            String name = "on: cow at " + c[0] + ", " + c[1];
            checkKick(t, name, drop, (Direction) c[2], 1.0);
            t.check(name + ": motion sent at once", drop.impulse).isTrue();
        }
        config().legacyItemDrops = false;
        for (Object[] c : cases) {
            checkNoKick(t, "off: cow at " + c[0] + ", " + c[1], cowDrop(t, 4.0 + (double) c[0], 4.0 + (double) c[1]));
        }
    }

    /** An item half in the ground at (4.3, 4.6), its middle 0.3 from the west side: kicked west in both versions */
    private static void kickKeepsSpeeds(TestContext t) {
        Vec3 motion = new Vec3(0.05, 0.2, 0.07);
        checkKick(t, "on", placed(t, 4.3, -ITEM_HALF, 4.6, motion), Direction.WEST, 1.0);
        config().legacyItemDrops = false;
        checkKick(t, "off", placed(t, 4.3, -ITEM_HALF, 4.6, motion), Direction.WEST, VANILLA_KEEP);
    }

    /**
     * An item half in the ground, kicked west, already moving west at the middle of the kick speeds and falling at
     * its top speed: one tick changes its motion by less than 0.1, so 1.21.1 would not send it at once
     * ({@code ItemEntity.tick} sends changes over 0.1)
     */
    private static void kickSentAtOnce(TestContext t) {
        Drop drop = placed(t, 4.3, -ITEM_HALF, 4.6, new Vec3(-0.2 * AIR, -GRAVITY * AIR / (1.0 - AIR), 0.0));
        checkKick(t, "on", drop, Direction.WEST, 1.0);
        t.check("on: motion changed by less than 0.1", drop.after.distanceTo(drop.before)).atMost(0.1);
        t.check("on: motion sent at once", drop.impulse).isTrue();
    }

    /** An item half in the ground in the middle of a block: every side is 0.5 away */
    private static void kickTie(TestContext t) {
        Vec3 motion = new Vec3(0.05, 0.2, 0.07);
        checkKick(t, "on", placed(t, 4.5, -ITEM_HALF, 4.5, motion), Direction.WEST, 1.0);
        config().legacyItemDrops = false;
        checkKick(t, "off", placed(t, 4.5, -ITEM_HALF, 4.5, motion), Direction.NORTH, VANILLA_KEEP);
    }

    private static void closedSides(TestContext t) {
        // the cow stands 0.1 from the west side and 0.4 from the north side of block (4, 0, 4)
        t.site().set(3, 0, 4, "stone");
        checkKick(t, "on: stone to the west", cowDrop(t, 4.1, 4.4), Direction.NORTH, 1.0);
        t.site().set(3, 0, 4, "stone_slab[type=bottom]");
        checkKick(t, "on: slab to the west", cowDrop(t, 4.1, 4.4), Direction.WEST, 1.0);

        config().legacyItemDrops = false;
        Vec3 motion = new Vec3(0.05, 0.2, 0.07);
        t.site().set(3, 0, 4, "stone");
        checkKick(t, "off: stone to the west", placed(t, 4.1, -ITEM_HALF, 4.4, motion), Direction.NORTH,
            VANILLA_KEEP);
        t.site().set(3, 0, 4, "stone_slab[type=bottom]");
        checkKick(t, "off: slab to the west", placed(t, 4.1, -ITEM_HALF, 4.4, motion), Direction.WEST, VANILLA_KEEP);
    }

    private static void onlyUp(TestContext t) {
        // a stone plate at y 2 with air under it; the item's middle is 0.1 above the plate's bottom, in its middle
        t.site().fill(3, 2, 3, 5, 2, 5, "stone");
        Vec3 motion = new Vec3(0.05, -0.2, 0.07);
        checkKick(t, "on", placed(t, 4.5, 2.1 - ITEM_HALF, 4.5, motion), Direction.UP, 1.0);
        config().legacyItemDrops = false;
        checkKick(t, "off", placed(t, 4.5, 2.1 - ITEM_HALF, 4.5, motion), Direction.UP, VANILLA_KEEP);
    }

    /** An item inside a boat, in the air of block (4, 0, 4), its middle 0.3 from the west side */
    private static Drop inBoat(TestContext t, Spawned<Boat> boat) {
        return t.onServer(level -> {
            Entity entity = level.getEntity(boat.id());
            t.require(entity != null, "The boat is gone");
            return tickOnce(entity.getY(), new ItemEntity(level, ORIGIN.getX() + 4.3, entity.getY() + 0.2,
                ORIGIN.getZ() + 4.6, new ItemStack(Items.LEATHER), 0.05, 0.2, 0.07));
        });
    }

    private static void insideBoat(TestContext t) {
        Spawned<Boat> boat = t.spawn(EntityType.BOAT, 4.5, 0.0, 4.5, 0.0F, null);
        checkNoKick(t, "on", inBoat(t, boat));
        config().legacyItemDrops = false;
        checkKick(t, "off", inBoat(t, boat), Direction.WEST, VANILLA_KEEP);
    }

    /**
     * A cow drop that the server ticks by itself; after 15 ticks (before the server's regular position update after
     * 20) returns {how far the server's item moved along x, how far apart the server's and the client's item are}
     */
    private static double[] serverAndClient(TestContext t) {
        Vec3 start = new Vec3(ORIGIN.getX() + 4.3, ORIGIN.getY(), ORIGIN.getZ() + 4.6);
        int id = t.onServer(level -> {
            Entity cow = create(t, level, EntityType.COW, 4.3, 0.0, 4.6);
            ItemEntity item = cow.spawnAtLocation(new ItemStack(Items.LEATHER));
            t.require(item != null, "No item dropped");
            return item.getId();
        });
        t.ticks(15);
        Vec3 server = t.onServer(level -> {
            Entity item = level.getEntity(id);
            t.require(item != null, "The server's item is gone");
            return item.position();
        });
        Vec3 client = t.onClientThread(() -> {
            ClientLevel level = t.level();
            Entity item = level.getEntity(id);
            t.require(item != null, "The client's item is gone");
            return item.position();
        });
        t.removeEntities();
        return new double[] {server.x - start.x, server.subtract(client).horizontalDistance()};
    }

    private static void clientSeesKick(TestContext t) {
        // the player stays at the site's home, far from the item, so it is not picked up
        // (a client that missed the kick is about 0.9 blocks off by then)
        double[] on = serverAndClient(t);
        t.check("on: the item went west, kicked (1.7.10)", on[0]).atMost(-0.5);
        t.check("on: client and server agree", on[1]).atMost(CLIENT_TOLERANCE);
        config().legacyItemDrops = false;
        t.check("off: client and server agree", serverAndClient(t)[1]).atMost(CLIENT_TOLERANCE);
    }
}

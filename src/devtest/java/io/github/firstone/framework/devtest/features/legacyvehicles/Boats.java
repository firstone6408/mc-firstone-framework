package io.github.firstone.framework.devtest.features.legacyvehicles;

import io.github.firstone.framework.devtest.core.Spawned;
import io.github.firstone.framework.devtest.core.TestContext;
import io.github.firstone.framework.devtest.core.TestSite;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Boat helpers for the Legacy Vehicles tests: spawning, driving and measuring
 */
final class Boats {

    /** Height to spawn a boat at on the site's pools (their water's surface is about -0.11) */
    static final double ON_WATER = -0.1;

    /** Height to spawn a boat at on the site's floor (ice lanes, stone) */
    static final double ON_FLOOR = 0.0;

    private Boats() {}

    /**
     * Spawns a boat facing +z and lets it settle
     *
     * @param t     the test
     * @param type  {@code EntityType.BOAT} or {@code EntityType.CHEST_BOAT}
     * @param wood  the wood ({@code BAMBOO} makes a raft)
     * @param x     x
     * @param y     y ({@link #ON_WATER} or {@link #ON_FLOOR})
     * @param z     z
     * @param setup more changes before it is added (motion, items), may be null
     * @param <T>   the boat's class
     * @return the boat
     */
    static <T extends Boat> Spawned<T> spawn(TestContext t, EntityType<T> type, Boat.Type wood, double x, double y,
                                             double z, Consumer<T> setup) {
        Spawned<T> boat = t.spawn(type, x, y, z, 0.0F, entity -> {
            if (entity instanceof Boat created) {
                created.setVariant(wood);
            }
            if (setup != null) {
                setup.accept(entity);
            }
        });
        t.ticks(10);
        return boat;
    }

    /**
     * Spawns a plain boat facing +z and lets it settle
     *
     * @param t    the test
     * @param wood the wood
     * @param x    x
     * @param y    y
     * @param z    z
     * @return the boat
     */
    static Spawned<Boat> boat(TestContext t, Boat.Type wood, double x, double y, double z) {
        return spawn(t, EntityType.BOAT, wood, x, y, z, null);
    }

    /**
     * Holds a key for some ticks and returns the boat's highest speed meanwhile
     *
     * @param t     the test
     * @param boat  the boat the player drives
     * @param key   the key to hold
     * @param ticks how long
     * @return the highest horizontal speed, in blocks per tick
     */
    static double drive(TestContext t, Spawned<? extends Boat> boat, KeyMapping key, int ticks) {
        double max = 0.0;
        t.hold(key);
        for (int i = 0; i < ticks; i++) {
            t.tick();
            max = Math.max(max, boat.speed());
        }
        t.release(key);
        return max;
    }

    /**
     * Returns the height of the player's eyes above the bottom of the boat they sit in
     *
     * @param t    the test
     * @param boat the boat
     * @return the height in blocks (1.7.10: 1.12)
     */
    static double eyeAboveBoat(TestContext t, Spawned<? extends Boat> boat) {
        Boat client = boat.client();
        t.require(client != null, "The boat is gone");
        return t.player().getEyeY() - client.getY();
    }

    /**
     * Counts the items lying on the site plus those in the player's inventory (a creative player picks up drops)
     *
     * @param t the test
     * @return item id path to count, sorted, e.g. {@code {oak_planks=3, stick=2}}
     */
    static String drops(TestContext t) {
        Map<String, Integer> counts = new TreeMap<>();
        t.onServer(level -> {
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, siteBox())) {
                add(counts, item.getItem());
            }
            return null;
        });
        t.withServerPlayer(player -> {
            for (ItemStack stack : player.getInventory().items) {
                add(counts, stack);
            }
        });
        return counts.toString();
    }

    /**
     * Removes the items lying on the site and empties the player's inventory
     *
     * @param t the test
     */
    static void clearDrops(TestContext t) {
        t.onServer(level -> {
            level.getEntitiesOfClass(ItemEntity.class, siteBox()).forEach(ItemEntity::discard);
            return null;
        });
        t.withServerPlayer(player -> player.getInventory().clearContent());
    }

    private static AABB siteBox() {
        return new AABB(Vec3.atLowerCornerOf(TestSite.ORIGIN.offset(TestSite.MIN, -4, TestSite.MIN)),
            Vec3.atLowerCornerOf(TestSite.ORIGIN.offset(TestSite.MAX + 1, 20, TestSite.MAX + 1)));
    }

    private static void add(Map<String, Integer> counts, ItemStack stack) {
        if (!stack.isEmpty()) {
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            counts.put(id, counts.getOrDefault(id, 0) + stack.getCount());
        }
    }

    /**
     * Fills slot 0 of a chest boat (used as {@code setup} of {@link #spawn})
     *
     * @param stack the items
     * @return the setup
     */
    static Consumer<ChestBoat> withItem(ItemStack stack) {
        return boat -> boat.setItem(0, stack);
    }
}

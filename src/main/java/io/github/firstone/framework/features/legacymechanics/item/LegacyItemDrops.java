package io.github.firstone.framework.features.legacymechanics.item;

import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import io.github.firstone.framework.features.legacymechanics.physics.PushOutOfBlocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;

/**
 * 1.7.10 item drops from entities: where the items appear and how they get out of the blocks they appear in
 *
 * <p>Items drop with the same speeds and fall the same way in both versions. The difference is the height: the
 * position of a 1.7.10 {@code EntityItem} was the middle of its box ({@code yOffset = height / 2}), so an item
 * dropped at an entity's feet ({@code entityDropItem}: loot, equipment, eggs, wool…) started 0.125 inside the
 * ground, and every tick an item inside a block was kicked 0.1 to 0.3 blocks per tick towards the nearest open side
 * of that block ({@code pushOutOfBlocks}). That kick is the wide, lively scatter of 1.7.10 drops. A 1.21.1 item
 * stands on the bottom of its box and lands on the ground without a kick; when one is kicked, 1.21.1 tries the sides
 * in another order and also slows the item's other directions to 0.75.</p>
 *
 * <p>The drops this changes are the 1.7.10 ones: {@code Entity.spawnAtLocation} (every entity's drops), items
 * dropped or thrown by players, a sheared mooshroom's mushrooms, a fishing catch and a dropped eye of ender. Their
 * height is lowered by 0.125. Boats, minecarts and falling blocks had their position in their middle in 1.7.10 too,
 * so their drops appear that much higher. Block drops already use the middle of the box in 1.21.1; drops of
 * entities 1.7.10 did not have keep their own height, unless they come from {@code spawnAtLocation}.</p>
 */
public final class LegacyItemDrops {

    /** Half the height of an item: how much lower a 1.7.10 item appeared (its position was the middle of its box) */
    private static final double ITEM_HALF_HEIGHT = EntityType.ITEM.getHeight() / 2.0;

    /** Half the height of a 1.7.10 boat (0.6), whose position was its middle */
    private static final double BOAT_HALF_HEIGHT = 0.3;

    /** Half the height of a 1.7.10 minecart (0.7), whose position was its middle */
    private static final double MINECART_HALF_HEIGHT = 0.35;

    /** Half the height of a 1.7.10 falling block (0.98), whose position was its middle */
    private static final double FALLING_BLOCK_HALF_HEIGHT = 0.49;

    private LegacyItemDrops() {}

    private static boolean enabled() {
        return LegacyMechanicsFeature.getConfig().legacyItemDrops;
    }

    /**
     * Returns the height an item dropped by an entity appears at
     *
     * <p>Called on the server when the item is created.</p>
     *
     * @param source the entity dropping the item
     * @param y      the height 1.21.1 uses (the bottom of the item's box)
     * @return the 1.7.10 height when "Legacy Item Drops" is on, otherwise {@code y}
     */
    public static double dropY(Entity source, double y) {
        return enabled() ? y + legacyMiddle(source) - ITEM_HALF_HEIGHT : y;
    }

    /** How far above its bottom a 1.7.10 entity had its position (0 for all but boats, minecarts and falling blocks) */
    private static double legacyMiddle(Entity entity) {
        if (entity instanceof Boat) {
            return BOAT_HALF_HEIGHT;
        }
        if (entity instanceof AbstractMinecart) {
            return MINECART_HALF_HEIGHT;
        }
        if (entity instanceof FallingBlockEntity) {
            return FALLING_BLOCK_HALF_HEIGHT;
        }
        return 0.0;
    }

    /**
     * Tells whether an item is free to move, the check that decides whether it gets kicked out of a block
     *
     * <p>Called on the server every item tick in place of 1.21.1's {@code noCollision} while
     * {@link #kicksLikeLegacy()} is true. 1.21.1 also counts entities the item is inside (like boats) and the world
     * border; 1.7.10 only blocks.</p>
     *
     * @param item the item
     * @return true unless the item is inside a block (a block it only touches does not count)
     */
    public static boolean isFree(ItemEntity item) {
        return !PushOutOfBlocks.isInBlock(item);
    }

    /**
     * Tells whether the 1.7.10 check and kick out of blocks replace the 1.21.1 ones
     *
     * @return true when "Legacy Item Drops" is on
     */
    public static boolean kicksLikeLegacy() {
        return enabled();
    }

    /**
     * Kicks an item that is inside a block towards the nearest open side, like 1.7.10 ({@link PushOutOfBlocks})
     *
     * <p>1.7.10 clients kicked their items themselves; a 1.21.1 client does not, and 1.21.1 sends an item's motion
     * at once only when it changed by more than 0.1 in a tick, so the kick is always sent at once.</p>
     *
     * @param item the item
     * @param x    the middle of the item, x
     * @param y    the middle of the item, y
     * @param z    the middle of the item, z
     */
    public static void pushOutOfBlocks(ItemEntity item, double x, double y, double z) {
        PushOutOfBlocks.push(item, x, y, z);
        item.hasImpulse = true;
    }
}

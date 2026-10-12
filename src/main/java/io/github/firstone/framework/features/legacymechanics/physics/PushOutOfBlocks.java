package io.github.firstone.framework.features.legacymechanics.physics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The 1.7.10 kick of an entity out of the block it is in ({@code Entity.pushOutOfBlocks}), used by item drops and
 * experience orbs
 *
 * <p>1.7.10 kicked dropped items and experience orbs out of blocks every tick, on both sides. 1.21.1 still does
 * ({@code Entity.moveTowardsClosestSpace}), but it tries the sides in another order and slows the entity's other
 * directions to 0.75; it also counts entities and the world border, where 1.7.10 only counted blocks.</p>
 */
public final class PushOutOfBlocks {

    /** Shrinks an entity's box before looking for blocks in it, so a block it only touches does not count */
    private static final double TOUCH_MARGIN = 1.0E-7;

    private PushOutOfBlocks() {}

    /**
     * Tells whether an entity is inside a block, the check that decides whether it gets kicked out
     *
     * @param entity the entity (an item or an experience orb)
     * @return true if its box is inside a block (a block it only touches does not count)
     */
    public static boolean isInBlock(Entity entity) {
        return entity.level().getBlockCollisions(entity, entity.getBoundingBox().deflate(TOUCH_MARGIN)).iterator()
            .hasNext();
    }

    /**
     * Kicks an entity that is inside a block towards the nearest open side, like 1.7.10
     *
     * <p>The sides are tried in the 1.7.10 order (west, east, up, north, south; never down), a side counts when its
     * block is not a full block, and the nearest one wins (on a tie, the first). Only that direction's speed is set,
     * to 0.1–0.3; the others are kept.</p>
     *
     * @param entity the entity
     * @param x      the middle of the entity, x
     * @param y      the middle of the entity, y
     * @param z      the middle of the entity, z
     */
    public static void push(Entity entity, double x, double y, double z) {
        Level level = entity.level();
        BlockPos pos = BlockPos.containing(x, y, z);
        double inX = x - pos.getX();
        double inY = y - pos.getY();
        double inZ = z - pos.getZ();
        int side = 3;
        double nearest = 9999.0;
        if (isOpen(level, pos.west()) && inX < nearest) {
            nearest = inX;
            side = 0;
        }
        if (isOpen(level, pos.east()) && 1.0 - inX < nearest) {
            nearest = 1.0 - inX;
            side = 1;
        }
        if (isOpen(level, pos.above()) && 1.0 - inY < nearest) {
            nearest = 1.0 - inY;
            side = 3;
        }
        if (isOpen(level, pos.north()) && inZ < nearest) {
            nearest = inZ;
            side = 4;
        }
        if (isOpen(level, pos.south()) && 1.0 - inZ < nearest) {
            side = 5;
        }
        float speed = entity.getRandom().nextFloat() * 0.2F + 0.1F;
        Vec3 motion = entity.getDeltaMovement();
        switch (side) {
            case 0 -> entity.setDeltaMovement(-speed, motion.y, motion.z);
            case 1 -> entity.setDeltaMovement(speed, motion.y, motion.z);
            case 4 -> entity.setDeltaMovement(motion.x, motion.y, -speed);
            case 5 -> entity.setDeltaMovement(motion.x, motion.y, speed);
            default -> entity.setDeltaMovement(motion.x, speed, motion.z);
        }
    }

    /** True if an entity can be kicked into this block (it is not a full block) */
    private static boolean isOpen(Level level, BlockPos pos) {
        return !level.getBlockState(pos).isCollisionShapeFullBlock(level, pos);
    }
}

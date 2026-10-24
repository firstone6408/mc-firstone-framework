package io.github.firstone.framework.common.boat;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import io.github.firstone.framework.mixin.boat.BoatAccessor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * 1.7.10 boat motion ({@code EntityBoat.onUpdate}), shared by the client (the boat the local player drives, Legacy
 * Vehicles) and the server (boats without a driver, Legacy Mechanics)
 *
 * <p>One tick is split around the boat's move: {@link Handling#beforeMove} (floating, the driver's push, the speed
 * limit, land friction) and {@link Handling#afterMove} (drag, turning toward the motion).</p>
 *
 * <p>On slippery blocks (friction above 0.6, like ice) the caller may ask for 1.21.1 sliding instead of the 1.7.10
 * land friction: the block's friction slows the boat, then the 1.21.1 push is added, and there is no speed limit, so
 * the boat goes as fast as in 1.21.1. 1.7.10 boats could not slide on ice. A boat that leaves the ice faster than the
 * 1.7.10 speed limit slows down to it like a 1.21.1 boat in water (keeping about {@value #SLIDE_OFF_DECAY} of its
 * speed per tick) instead of stopping at once.</p>
 */
public final class LegacyBoatPhysics {

    /** Height of the 1.7.10 boat, used for floating so the boat floats as high as in 1.7.10 (1.21.1: 0.5625) */
    private static final double BOAT_HEIGHT = 0.6;

    /**
     * Whether the server running inside this game (singleplayer) moves boats without a driver the 1.7.10 way; the
     * rule belongs to Legacy Mechanics ("Legacy Boat Coasting"), which sets it here when it starts, so Legacy
     * Vehicles can ask without knowing Legacy Mechanics. On by default, like the option.
     */
    private static BooleanSupplier localServerCoasting = () -> true;

    /** Highest speed in blocks per tick (on slippery blocks with 1.21.1 sliding: none) */
    private static final double MAX_SPEED = 0.35;

    /** Lowest value of the speed-up factor (it grows toward {@link #MAX_SPEED} while the boat speeds up) */
    private static final double MIN_SPEED_FACTOR = 0.07;

    /** Speed above which a boat no longer slows down after hitting a wall (it breaks on a 1.7.10 server) */
    private static final double CRASH_SPEED = 0.2;

    /** Turn of the boat toward its motion, at most this many degrees per tick */
    private static final float MAX_TURN = 20.0F;

    /** Friction of ordinary blocks; blocks above it (ice, packed ice, blue ice, slime) are slippery */
    private static final float NORMAL_FRICTION = 0.6F;

    /** Vertical speed below which a boat in water only bobs (it does not fall into the water) */
    private static final double BOB_SPEED = 0.1;

    /** Push between two touching entities, per tick ({@code Entity.applyEntityCollision}) */
    private static final double STAND_PUSH = 0.05;

    /** How far from the boat's top someone's feet may be to count as standing on it (the boat bobs) */
    private static final double STAND_TOLERANCE = 0.05;

    /**
     * Part of its speed a boat that left slippery blocks keeps per tick while faster than {@value #MAX_SPEED} (the
     * 1.21.1 friction of water and air)
     */
    private static final double SLIDE_OFF_DECAY = 0.9;

    /** 1.21.1 push of a driver pressing forward / backward ({@code Boat.controlBoat}) */
    private static final double SLIDE_PUSH_FORWARD = 0.04;
    private static final double SLIDE_PUSH_BACKWARD = 0.005;

    private LegacyBoatPhysics() {}

    /**
     * Sets where the coasting rule of the server inside this game is read from
     *
     * <p>Called once by Legacy Mechanics when it starts (also on the physical client, where singleplayer's server
     * runs).</p>
     *
     * @param rule tells, each time it is asked, whether boats without a driver move the 1.7.10 way
     */
    public static void setLocalServerCoasting(BooleanSupplier rule) {
        localServerCoasting = rule;
    }

    /**
     * Tells whether the server running inside this game (singleplayer) moves boats without a driver the 1.7.10 way
     *
     * <p>Used by Legacy Vehicles on the client to move such boats the same way; on a server elsewhere it is not
     * asked.</p>
     *
     * @return the current "Legacy Boat Coasting" option of the server inside this game
     */
    public static boolean localServerCoasts() {
        return localServerCoasting.getAsBoolean();
    }

    /**
     * Tells whether a ground friction is slippery
     *
     * @param friction friction of the blocks under a boat ({@code Block.getFriction}, averaged by the boat)
     * @return true above the friction of ordinary blocks (0.6)
     */
    public static boolean isSlippery(float friction) {
        return friction > NORMAL_FRICTION;
    }

    /**
     * Tells whether a boat is a plain wooden boat, the only boat 1.7.10 had
     *
     * @param boat any boat
     * @return false for chest boats and bamboo rafts
     */
    public static boolean isWoodenBoat(Boat boat) {
        return boat.getType() == EntityType.BOAT && boat.getVariant() != Boat.Type.BAMBOO;
    }

    /**
     * Tells whether a boat floated in water at its last tick
     *
     * @param boat the boat
     * @return true if its status is {@code IN_WATER}
     */
    public static boolean isFloating(Boat boat) {
        return ((BoatAccessor) boat).framework$getStatus() == Boat.Status.IN_WATER;
    }

    /** Tells whether a boat stood on blocks at the start of its tick (status {@code ON_LAND}) */
    private static boolean onLand(Boat boat) {
        return ((BoatAccessor) boat).framework$getStatus() == Boat.Status.ON_LAND;
    }

    /**
     * Returns the friction a boat slides with when it stands on slippery blocks (ice, packed ice, blue ice, slime)
     *
     * @param boat the boat
     * @return the average friction under it when it is on land and that friction is slippery, else 0
     */
    public static float slideFriction(Boat boat) {
        BoatAccessor state = (BoatAccessor) boat;
        float friction = state.framework$getLandFriction();
        return state.framework$getStatus() == Boat.Status.ON_LAND && isSlippery(friction) ? friction : 0.0F;
    }

    /**
     * Tells whether a boat only bobs on the water: it is in water and moves up or down slowly (a boat floating with
     * the 1.7.10 motion bobs across the water's surface; a boat falling into water moves down fast)
     *
     * @param boat the boat
     * @return true when it is in water and its vertical speed is below {@value #BOB_SPEED}
     */
    public static boolean isBobbing(Boat boat) {
        return isFloating(boat) && Math.abs(boat.getDeltaMovement().y) < BOB_SPEED;
    }

    /** Motion state of one boat, kept between ticks */
    public static final class Handling {

        /** Speed-up factor ({@code speedMultiplier}), from {@value #MIN_SPEED_FACTOR} to {@value #MAX_SPEED} */
        private double speedFactor = MIN_SPEED_FACTOR;

        /** Horizontal speed at the start of the tick */
        private double startSpeed;

        /** This tick uses 1.21.1 sliding on a slippery block */
        private boolean sliding;

        /** The boat still has speed above {@value #MAX_SPEED} from sliding on a slippery block */
        private boolean slidOff;

        /**
         * Returns the boat's horizontal speed at the start of the last tick, before the driver's push and the land
         * friction (1.7.10 checks it for crashes)
         *
         * @return the speed in blocks per tick
         */
        public double startSpeed() {
            return this.startSpeed;
        }

        /**
         * First half of the 1.7.10 tick, before the boat moves: floating, the driver's push, the speed limit, the
         * speed-up factor and the land friction (or the slide on a slippery block)
         *
         * @param boat          the boat
         * @param forward       1 when the driver presses forward, -1 backward, 0 neither (or no driver)
         * @param strafe        1 when the driver presses left, -1 right, 0 neither
         * @param slideFriction friction of the slippery block the boat slides on with 1.21.1 sliding, or 0 to use
         *                      the 1.7.10 land friction
         */
        public void beforeMove(Boat boat, int forward, int strafe, float slideFriction) {
            Vec3 motion = boat.getDeltaMovement();
            double x = motion.x;
            double y = motion.y;
            double z = motion.z;
            this.startSpeed = Math.sqrt(x * x + z * z);
            this.sliding = boat.onGround() && isSlippery(slideFriction);

            // floating: the share of the 1.7.10 boat height (lowered by 0.125) that is in water, in 5 slices
            double inWater = waterShare(boat);
            if (inWater < 1.0) {
                y += 0.04F * (inWater * 2.0 - 1.0);
            } else {
                if (y < 0.0) {
                    y /= 2.0;
                }
                y += 0.007F;
            }

            if (this.sliding) {
                // 1.21.1 slows the boat by the block's friction before the driver's push
                x *= slideFriction;
                z *= slideFriction;
            }

            // the driver pushes the boat toward where they look; strafing turns that push by 90 degrees
            Entity driver = boat.getControllingPassenger();
            if (driver != null && forward != 0) {
                float moveForward = forward * 0.98F;
                float angle = (driver.getYRot() - strafe * 0.98F * 90.0F) * Mth.DEG_TO_RAD;
                double push = this.sliding
                    ? (forward > 0 ? SLIDE_PUSH_FORWARD : SLIDE_PUSH_BACKWARD)
                    : this.speedFactor * moveForward * 0.05F;
                x += -Math.sin(angle) * push;
                z += Math.cos(angle) * push;
            }

            double speed = Math.sqrt(x * x + z * z);
            double limit = this.slidOff ? Math.max(MAX_SPEED, this.startSpeed * SLIDE_OFF_DECAY) : MAX_SPEED;
            if (speed > limit && !this.sliding) {
                x *= limit / speed;
                z *= limit / speed;
                speed = limit;
            }
            this.slidOff = this.sliding || (this.slidOff && speed > MAX_SPEED);

            if (speed > this.startSpeed && this.speedFactor < MAX_SPEED) {
                this.speedFactor = Math.min(MAX_SPEED, this.speedFactor + (MAX_SPEED - this.speedFactor) / 35.0);
            } else {
                this.speedFactor = Math.max(MIN_SPEED_FACTOR,
                    this.speedFactor - (this.speedFactor - MIN_SPEED_FACTOR) / 35.0);
            }

            if (this.sliding) {
                y *= 0.5;
            } else if (boat.onGround() && (!this.slidOff || onLand(boat))) {
                // leaving the ice the boat still touched it at its last move: only halve on land, not over water
                x *= 0.5;
                y *= 0.5;
                z *= 0.5;
            }
            boat.setDeltaMovement(x, y, z);
        }

        /**
         * Second half of the 1.7.10 tick, after the boat moved: drag (none after a fast crash, nor while sliding),
         * and the boat turns toward where it moved, at most {@value #MAX_TURN} degrees per tick
         *
         * @param boat the boat
         */
        public void afterMove(Boat boat) {
            if (!this.sliding && (!boat.horizontalCollision || !(this.startSpeed > CRASH_SPEED))) {
                boat.setDeltaMovement(boat.getDeltaMovement().multiply(0.99F, 0.95F, 0.99F));
            }

            boat.setXRot(0.0F);
            double dx = boat.getX() - boat.xo;
            double dz = boat.getZ() - boat.zo;
            float yaw = boat.getYRot();
            if (dx * dx + dz * dz > 0.001) {
                float target = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
                yaw += Mth.clamp(Mth.wrapDegrees(target - yaw), -MAX_TURN, MAX_TURN);
            }
            boat.setYRot(yaw);
        }
    }

    /**
     * Pushes a boat and everyone standing on it apart, like 1.7.10: each living entity pushed the entities its box
     * (0.2 wider) touched ({@code EntityLivingBase.collideWithNearbyEntities} → {@code Entity.applyEntityCollision}),
     * and its feet touched the bobbing boat it stood on. The boat moves away from where they stand and they are pushed
     * the other way, so the boat slides out from under them (1.21.1 does not push a boat from above,
     * {@code Boat.push}).
     *
     * @param boat       the boat
     * @param moveBoat   push the boat too (only the side that moves the boat pushes it: the server for boats without
     *                   a player driver, the client for the boat it moves)
     * @param pushedBack tells which standing entities are pushed back on this side (each side moves its own entities:
     *                   the server mobs, the client its own player)
     */
    public static void pushStanders(Boat boat, boolean moveBoat, Predicate<Entity> pushedBack) {
        AABB box = boat.getBoundingBox();
        AABB reach = box.inflate(0.2, STAND_TOLERANCE, 0.2);
        for (Entity entity : boat.level().getEntities(boat, reach, entity -> entity instanceof LivingEntity
            && !entity.isPassenger() && Math.abs(entity.getBoundingBox().minY - box.maxY) <= STAND_TOLERANCE)) {
            push(boat, entity, moveBoat, pushedBack.test(entity));
        }
    }

    /**
     * Pushes two entities apart like 1.7.10 {@code Entity.applyEntityCollision}: 0.05 per tick away from each other,
     * less when they are far apart, nothing when their centers are closer than 0.01
     */
    private static void push(Entity boat, Entity other, boolean pushBoat, boolean pushOther) {
        double dx = boat.getX() - other.getX();
        double dz = boat.getZ() - other.getZ();
        double distance = Math.max(Math.abs(dx), Math.abs(dz));
        if (distance >= 0.01) {
            distance = Math.sqrt(distance);
            double scale = Math.min(1.0, 1.0 / distance) / distance * STAND_PUSH;
            if (pushBoat) {
                boat.setDeltaMovement(boat.getDeltaMovement().add(dx * scale, 0.0, dz * scale));
            }
            if (pushOther) {
                other.setDeltaMovement(other.getDeltaMovement().add(-dx * scale, 0.0, -dz * scale));
            }
        }
    }

    /** Returns the share (0 to 1) of the 1.7.10 boat height, lowered by 0.125, that is in water, in 5 slices */
    private static double waterShare(Boat boat) {
        AABB box = boat.getBoundingBox();
        double share = 0.0;
        for (int slice = 0; slice < 5; slice++) {
            double bottom = box.minY + BOAT_HEIGHT * slice / 5.0 - 0.125;
            double top = box.minY + BOAT_HEIGHT * (slice + 1) / 5.0 - 0.125;
            if (inWater(boat.level(), new AABB(box.minX, bottom, box.minZ, box.maxX, top, box.maxZ))) {
                share += 1.0 / 5.0;
            }
        }
        return share;
    }

    /**
     * Tells whether water reaches into a box, like 1.7.10 {@code World.isAABBInMaterial}: a water block counts when
     * its surface (full for still and falling water, lower for flowing water) is at or above the box's bottom
     */
    private static boolean inWater(Level level, AABB box) {
        int minX = Mth.floor(box.minX);
        int maxX = Mth.floor(box.maxX + 1.0);
        int minY = Mth.floor(box.minY);
        int maxY = Mth.floor(box.maxY + 1.0);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.floor(box.maxZ + 1.0);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    FluidState fluid = level.getFluidState(pos.set(x, y, z));
                    if (fluid.is(FluidTags.WATER)) {
                        boolean full = fluid.isSource()
                            || fluid.hasProperty(FlowingFluid.FALLING) && fluid.getValue(FlowingFluid.FALLING);
                        double surface = y + 1.0 - (full ? 0.0 : (8 - fluid.getAmount()) / 8.0);
                        if (surface >= box.minY) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
}

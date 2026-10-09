package io.github.firstone.framework.features.animatium;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Client-side emulation of how a 1.7.10 server synced entity positions and rotations
 *
 * <p>1.7.10 {@code EntityTrackerEntry} encoded positions in 1/32 block units (with a width-dependent rounding for
 * X/Z, see {@link #encodeHorizontal}) and only sent a move when an axis changed by at least 4 units (0.125 block)
 * or every 60 ticks. Rotations (1/256 of a turn) were sent only when they changed by at least 4 units (5.6°), and
 * the head yaw was applied directly on the client, without smoothing. Teleports (moves of 4 blocks or more) were
 * always applied, 1/64 block higher. 1.21.1 sends far smaller changes and smooths the head, which is why modern movement looks smoother.</p>
 *
 * <p>The client receives the same samples as in 1.7.10 (the server still sends at the same tick rate), so applying
 * these rules to every received update reproduces the 1.7.10 stepping. The state lives on each entity
 * ({@link Holder}).</p>
 */
public final class LegacyEntitySync {

    /** Minimum change, in 1/32 block or 1/256 turn units, for an update to be applied (1.7.10 {@code >= 4}) */
    private static final int MIN_CHANGE = 4;

    /** A position change smaller than {@link #MIN_CHANGE} is still applied after this many ticks (1.7.10 {@code % 60}) */
    private static final int FORCED_SYNC_TICKS = 60;

    /**
     * Minimum squared velocity change for a velocity update to be sent (1.7.10 {@code var21 > 0.02 * 0.02})
     */
    private static final double MIN_VELOCITY_CHANGE_SQR = 0.02 * 0.02;

    /** Extra height added to teleports by the 1.7.10 client ({@code serverPosY / 32.0 + 0.015625}) */
    private static final double TELEPORT_Y_OFFSET = 0.015625;

    private LegacyEntitySync() {}

    /**
     * Last applied 1.7.10-encoded position and rotation of one entity
     */
    public static final class State {
        boolean initialized;
        int x;
        int y;
        int z;
        int yaw;
        int pitch;
        int lastPositionTick;
        boolean headInitialized;
        int headYaw;
        boolean velocityInitialized;
        double velocityX;
        double velocityY;
        double velocityZ;
    }

    /**
     * Implemented by every {@link Entity} through a mixin to carry its {@link State}
     */
    public interface Holder {

        /**
         * Returns the legacy sync state of this entity, creating it on first use
         *
         * @return the state, never null
         */
        State animatium$getSyncState();
    }

    /**
     * Result of a filtered position/rotation update
     *
     * @param x     X to interpolate to
     * @param y     Y to interpolate to
     * @param z     Z to interpolate to
     * @param yaw   yaw to interpolate to (degrees)
     * @param pitch pitch to interpolate to (degrees)
     */
    public record Target(double x, double y, double z, float yaw, float pitch) {}

    /**
     * Applies the 1.7.10 send rules to a received move/rotation update
     *
     * @param entity the entity being updated
     * @param x      received X
     * @param y      received Y
     * @param z      received Z
     * @param yaw    received yaw (degrees)
     * @param pitch  received pitch (degrees)
     * @return where to interpolate to, or {@code null} if 1.7.10 would not have sent anything
     */
    public static Target filterMove(Entity entity, double x, double y, double z, float yaw, float pitch) {
        State state = stateOf(entity);
        int ex = encodeHorizontal(x, entity.getBbWidth());
        int ey = encodeVertical(y);
        int ez = encodeHorizontal(z, entity.getBbWidth());
        int eYaw = encodeReceivedAngle(yaw);
        int ePitch = encodeReceivedAngle(pitch);

        boolean positionMoved = ex != state.x || ey != state.y || ez != state.z;
        boolean sendPosition = Math.abs(ex - state.x) >= MIN_CHANGE || Math.abs(ey - state.y) >= MIN_CHANGE
            || Math.abs(ez - state.z) >= MIN_CHANGE
            || (positionMoved && entity.tickCount - state.lastPositionTick >= FORCED_SYNC_TICKS);
        boolean sendRotation = Math.abs(angleDifference(eYaw, state.yaw)) >= MIN_CHANGE
            || Math.abs(angleDifference(ePitch, state.pitch)) >= MIN_CHANGE;

        if (!sendPosition && !sendRotation) {
            return null;
        }
        if (sendPosition) {
            state.x = ex;
            state.y = ey;
            state.z = ez;
            state.lastPositionTick = entity.tickCount;
        }
        if (sendRotation) {
            state.yaw = eYaw;
            state.pitch = ePitch;
        }
        return new Target(state.x / 32.0, state.y / 32.0, state.z / 32.0, decodeAngle(state.yaw), decodeAngle(state.pitch));
    }

    /**
     * Applies the 1.7.10 rules to a received teleport
     *
     * <p>1.21.1 also sends a teleport whenever an entity leaves or touches the ground (every jump and every knockback).
     * 1.7.10 only teleported when the move did not fit in a relative move (4 blocks or more on an axis); everything
     * else was a normal move, subject to the 0.125-block threshold. So a teleport within that range is handled like a
     * move ({@link #filterMove}); only a real 1.7.10 teleport is always applied, 1/64 block higher.</p>
     *
     * @param entity the entity being teleported
     * @param x      received X
     * @param y      received Y
     * @param z      received Z
     * @param yaw    received yaw (degrees)
     * @param pitch  received pitch (degrees)
     * @return where to interpolate to, or {@code null} if 1.7.10 would not have sent anything
     */
    public static Target filterTeleport(Entity entity, double x, double y, double z, float yaw, float pitch) {
        State state = stateOf(entity);
        int ex = encodeHorizontal(x, entity.getBbWidth());
        int ey = encodeVertical(y);
        int ez = encodeHorizontal(z, entity.getBbWidth());
        if (fitsRelativeMove(ex - state.x) && fitsRelativeMove(ey - state.y) && fitsRelativeMove(ez - state.z)) {
            return filterMove(entity, x, y, z, yaw, pitch);
        }
        state.x = ex;
        state.y = ey;
        state.z = ez;
        state.yaw = encodeReceivedAngle(yaw);
        state.pitch = encodeReceivedAngle(pitch);
        state.lastPositionTick = entity.tickCount;
        return new Target(state.x / 32.0, state.y / 32.0 + TELEPORT_Y_OFFSET, state.z / 32.0,
            decodeAngle(state.yaw), decodeAngle(state.pitch));
    }

    /** True if a change in 1/32 block units fits a 1.7.10 relative move byte ({@code -128 <= delta < 128}) */
    private static boolean fitsRelativeMove(int delta) {
        return delta >= -128 && delta < 128;
    }

    /**
     * Applies the 1.7.10 rule to a received head yaw
     *
     * @param entity  the entity whose head turned
     * @param headYaw received head yaw (degrees)
     * @return the head yaw to set directly, or {@code null} if the change is below the 1.7.10 threshold
     */
    public static Float filterHeadYaw(Entity entity, float headYaw) {
        State state = stateOf(entity);
        int encoded = encodeReceivedAngle(headYaw);
        if (!state.headInitialized) {
            state.headInitialized = true;
            state.headYaw = encodeReceivedAngle(entity.getYHeadRot());
        }
        if (Math.abs(angleDifference(encoded, state.headYaw)) < MIN_CHANGE) {
            return null;
        }
        state.headYaw = encoded;
        return decodeAngle(encoded);
    }

    /**
     * Applies the 1.7.10 send rule to a received velocity
     *
     * <p>1.7.10 {@code EntityTrackerEntry} only sent a velocity when it differed from the last sent one by more than
     * 0.02 (or when it became exactly zero). 1.21.1 sends every change above 0.0003. Knockback always passes: it
     * changes the velocity by far more than 0.02.</p>
     *
     * @param entity the entity whose velocity changed
     * @param x      received X velocity
     * @param y      received Y velocity
     * @param z      received Z velocity
     * @return true if 1.7.10 would have sent this velocity, false to ignore it
     */
    public static boolean acceptVelocity(Entity entity, double x, double y, double z) {
        State state = ((Holder) entity).animatium$getSyncState();
        if (!state.velocityInitialized) {
            Vec3 current = entity.getDeltaMovement();
            state.velocityInitialized = true;
            state.velocityX = current.x;
            state.velocityY = current.y;
            state.velocityZ = current.z;
        }
        double dx = x - state.velocityX;
        double dy = y - state.velocityY;
        double dz = z - state.velocityZ;
        double changeSqr = dx * dx + dy * dy + dz * dz;
        boolean becameZero = changeSqr > 0.0 && x == 0.0 && y == 0.0 && z == 0.0;
        if (changeSqr <= MIN_VELOCITY_CHANGE_SQR && !becameZero) {
            return false;
        }
        state.velocityX = x;
        state.velocityY = y;
        state.velocityZ = z;
        return true;
    }

    private static State stateOf(Entity entity) {
        State state = ((Holder) entity).animatium$getSyncState();
        if (!state.initialized) {
            // start from where the entity currently is (its spawn or last known position)
            state.initialized = true;
            state.x = encodeHorizontal(entity.lerpTargetX(), entity.getBbWidth());
            state.y = encodeVertical(entity.lerpTargetY());
            state.z = encodeHorizontal(entity.lerpTargetZ(), entity.getBbWidth());
            state.yaw = encodeReceivedAngle(entity.lerpTargetYRot());
            state.pitch = encodeReceivedAngle(entity.lerpTargetXRot());
            state.lastPositionTick = entity.tickCount;
        }
        return state;
    }

    /**
     * Encodes X or Z like 1.7.10 {@code Entity.EnumEntitySize.multiplyBy32AndRound}
     *
     * <p>The rounding direction depends on the entity width ({@code width % 2}), which in 1.7.10 made entities of
     * different sizes snap to the 1/32 grid in slightly different ways.</p>
     *
     * @param value position in blocks
     * @param width entity width in blocks
     * @return the position in 1/32 block units
     */
    static int encodeHorizontal(double value, float width) {
        double fraction = value - (Mth.floor(value) + 0.5);
        double scaled = value * 32.0;
        float size = width % 2.0F;
        if (size < 0.375F) {
            return !(fraction < 0.0 ? fraction < -0.3125 : fraction < 0.3125) ? Mth.floor(scaled) : Mth.ceil(scaled);
        } else if (size < 0.75F) {
            return !(fraction < 0.0 ? fraction < -0.3125 : fraction < 0.3125) ? Mth.ceil(scaled) : Mth.floor(scaled);
        } else if (size < 1.0F) {
            return fraction > 0.0 ? Mth.floor(scaled) : Mth.ceil(scaled);
        } else if (size < 1.375F) {
            return !(fraction < 0.0 ? fraction < -0.1875 : fraction < 0.1875) ? Mth.floor(scaled) : Mth.ceil(scaled);
        } else if (size < 1.75F) {
            return !(fraction < 0.0 ? fraction < -0.1875 : fraction < 0.1875) ? Mth.ceil(scaled) : Mth.floor(scaled);
        }
        return fraction > 0.0 ? Mth.ceil(scaled) : Mth.floor(scaled);
    }

    /**
     * Encodes Y like 1.7.10 ({@code floor(posY * 32)})
     *
     * @param value height in blocks
     * @return the height in 1/32 block units
     */
    static int encodeVertical(double value) {
        return Mth.floor(value * 32.0);
    }

    /**
     * Recovers the 1/256-turn byte of an angle received from the server
     *
     * <p>The server already sends angles as {@code floor(deg * 256 / 360)}; rounding recovers that exact value from
     * the decoded degrees.</p>
     *
     * @param degrees angle in degrees
     * @return the angle in 1/256 turn units (0–255)
     */
    static int encodeReceivedAngle(float degrees) {
        return Math.round(degrees * 256.0F / 360.0F) & 0xFF;
    }

    private static float decodeAngle(int encoded) {
        return (byte) encoded * 360 / 256.0F;
    }

    /** Signed difference between two 1/256-turn angles, wrapped to -128…127 */
    private static int angleDifference(int a, int b) {
        return (byte) (a - b);
    }
}

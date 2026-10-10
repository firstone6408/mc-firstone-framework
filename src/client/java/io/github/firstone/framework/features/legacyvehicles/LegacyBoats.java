package io.github.firstone.framework.features.legacyvehicles;

import io.github.firstone.framework.common.boat.BoatPushPayload;
import io.github.firstone.framework.common.boat.LegacyBoatPhysics;
import io.github.firstone.framework.features.legacymechanics.LegacyMechanicsFeature;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 1.7.10 boats ({@code EntityBoat}) on the client: which boats are changed, riding and splash, used by the mixins of
 * {@code client/mixin/legacyvehicles/}; the motion itself is {@link LegacyBoatPhysics}
 *
 * <p>Every boat is driven, ridden and splashes like 1.7.10; only the seat and the model are 1.7.10 for wooden boats
 * alone ({@link LegacyBoatPhysics#isWoodenBoat}), as chest boats and bamboo rafts did not exist in 1.7.10.</p>
 *
 * <p>1.21.1 lets the driver's client move the boat and send its position to the server, so the 1.7.10 handling of
 * the boat the local player drives works on any server. Boats nobody rides are also moved by the client, as in
 * 1.7.10 ({@link #simulates}); pushes between boats and entities come from the server ({@link #pushFromServer}).</p>
 */
public final class LegacyBoats {

    /** Speed above which the water splashes around the boat */
    private static final double SPLASH_SPEED = 0.26249999999999996;

    /** Distance from the server's boat beyond which a boat the client moves by itself is moved back to it */
    private static final double RESYNC_DISTANCE = 1.0;

    /** Distance within which the local player may stand on a boat (the boat is 1.375 wide) */
    private static final double NEAR_PLAYER = 3.0;

    /** How far behind the middle of the boat a lone player sits */
    private static final double SEAT_BACK = 0.4;

    /**
     * Height of a seated player's eyes above the bottom of the boat in 1.7.10: the boat position is 0.3 above its
     * bottom, the seat {@code getMountedYOffset} -0.3 below that, and the player's eyes {@code getYOffset} 1.12 above
     * the seat (1.21.1 puts them about 1.21 above it)
     */
    private static final double EYE_HEIGHT = 1.12;

    private LegacyBoats() {}

    /**
     * Tells whether the 1.7.10 handling applies to a boat on this side
     *
     * @param boat any boat
     * @return true on the client when "Legacy Handling" is on
     */
    public static boolean handles(Boat boat) {
        return boat.level().isClientSide() && LegacyVehiclesFeature.getConfig().legacyBoatHandling;
    }

    /**
     * Returns the friction to slide with on a slippery block (Ice Boating)
     *
     * @param boat the boat
     * @return the friction under it when "Ice Boating" is on and it stands on slippery blocks, else 0
     */
    public static float slideFriction(Boat boat) {
        return LegacyVehiclesFeature.getConfig().iceBoating ? LegacyBoatPhysics.slideFriction(boat) : 0.0F;
    }

    /**
     * Tells whether the client moves a boat by itself with the 1.7.10 motion, as 1.7.10 did for every boat nobody
     * else rode ({@code EntityBoat.isBoatEmpty} stayed false): the boat reacts at once to someone standing on it and
     * goes on smoothly after the local player gets out, instead of following the server's positions 10 ticks late
     *
     * <p>The server must move such boats with the 1.7.10 motion too ("Legacy Boat Coasting" of Legacy Mechanics). In
     * singleplayer that option is read directly; on a server, a boat that ends up more than {@value #RESYNC_DISTANCE}
     * block away from the server's is moved back to it ({@link #needsResync}).</p>
     *
     * @param boat a boat on the client
     * @return true when "Legacy Handling" is on and the boat has no driver on this client and no passenger
     */
    public static boolean simulates(Boat boat) {
        if (!handles(boat) || boat.isControlledByLocalInstance() || !boat.getPassengers().isEmpty()) {
            return false;
        }
        return !Minecraft.getInstance().hasSingleplayerServer()
            || LegacyMechanicsFeature.getConfig().legacyBoatCoasting;
    }

    /**
     * Tells whether a boat the client moves by itself is too far from the server's, like 1.7.10, which only took a
     * new position from the server when it was more than a block away
     *
     * @param boat   the boat the client moves
     * @param target the boat's position on the server
     * @return true beyond {@value #RESYNC_DISTANCE} block
     */
    public static boolean needsResync(Boat boat, Vec3 target) {
        return target.distanceToSqr(boat.position()) > RESYNC_DISTANCE * RESYNC_DISTANCE;
    }

    /**
     * Moves a boat without a driver for one tick on the client: pushes with the local player standing on it, then
     * the 1.7.10 motion
     *
     * @param boat     the boat
     * @param handling its motion state
     * @param slide    friction to slide with on a slippery block, or 0 ({@link #slideFriction})
     */
    public static void simulate(Boat boat, LegacyBoatPhysics.Handling handling, float slide) {
        Minecraft minecraft = Minecraft.getInstance();
        LegacyBoatPhysics.pushStanders(boat, true, entity -> entity == minecraft.player);
        handling.beforeMove(boat, 0, 0, slide);
        boat.move(MoverType.SELF, boat.getDeltaMovement());
        handling.afterMove(boat);
    }

    /**
     * Pushes with whoever stands on a boat the client neither drives nor moves by itself (it has passengers and
     * follows the server): only the local player standing on it is pushed back, the server pushes the boat
     *
     * @param boat the boat
     */
    public static void pushLocalStander(Boat boat) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.distanceToSqr(boat) <= NEAR_PLAYER * NEAR_PLAYER) {
            LegacyBoatPhysics.pushStanders(boat, false, entity -> entity == minecraft.player);
        }
    }

    /**
     * Pushes the boat the local player drives away from whoever stands on it, like 1.7.10 (they push themselves back
     * on their side); pushes by boats and entities touching its sides come from the server ({@link #pushFromServer})
     *
     * @param boat the boat
     */
    public static void pushDriven(Boat boat) {
        LegacyBoatPhysics.pushStanders(boat, true, entity -> false);
    }

    /**
     * Adds a push the server found for the boat the local player drives (a boat or an entity touched it), as 1.7.10
     * sent the new motion of a pushed boat to its driver
     *
     * @param minecraft the game
     * @param payload   the boat and its push
     */
    public static void pushFromServer(Minecraft minecraft, BoatPushPayload payload) {
        if (minecraft.level != null && minecraft.level.getEntity(payload.entityId()) instanceof Boat boat
            && boat.isControlledByLocalInstance()) {
            boat.setDeltaMovement(boat.getDeltaMovement().add(payload.x(), 0.0, payload.z()));
        }
    }

    /**
     * Tells whether the water splash of an entity entering water must be left out for a boat (on the client: its
     * particles), with "Legacy Handling"
     *
     * <p>A boat floating with the 1.7.10 motion has its bottom right at the water's surface and bobs across it, so
     * 1.21.1 would splash each time it dips back in. A boat that falls into water still splashes
     * ({@link LegacyBoatPhysics#isBobbing}).</p>
     *
     * @param entity an entity entering water on the client
     * @return true for a floating boat that only bobs
     */
    public static boolean skipsSplash(Entity entity) {
        return entity instanceof Boat boat && handles(boat) && LegacyBoatPhysics.isBobbing(boat);
    }

    /**
     * Tells whether the 1.7.10 riding rules apply to a passenger
     *
     * @param boat      the boat
     * @param passenger one of its passengers
     * @return true on the client, for a player, when "Legacy Riding" is on
     */
    public static boolean rides(Boat boat, Entity passenger) {
        return boat.level().isClientSide() && LegacyVehiclesFeature.getConfig().legacyBoatRiding
            && passenger instanceof Player;
    }

    /**
     * Returns the 1.7.10 seat of a player who is alone in a wooden boat, relative to the boat
     *
     * @param boat      the boat
     * @param passenger the player
     * @return the seat that puts the player's eyes {@value #EYE_HEIGHT} blocks above the bottom of the boat,
     *         {@value #SEAT_BACK} blocks behind its middle
     */
    public static Vec3 seat(Boat boat, Entity passenger) {
        // the player's own attachment point is placed on the seat, so it is added back
        double height = passenger.getVehicleAttachmentPoint(boat).y + EYE_HEIGHT - passenger.getEyeHeight();
        return new Vec3(0.0, height, -SEAT_BACK).yRot(-boat.getYRot() * Mth.DEG_TO_RAD);
    }

    /**
     * Spawns the 1.7.10 water splash around a fast boat, at both ends and along its sides
     *
     * @param boat the boat, after it moved this tick
     */
    public static void splash(Boat boat) {
        double dx = boat.getX() - boat.xo;
        double dz = boat.getZ() - boat.zo;
        double speed = Math.sqrt(dx * dx + dz * dz);
        if (!(speed > SPLASH_SPEED)) {
            return;
        }
        // 1.7.10 angles point from the front to the back: 90 degrees before the 1.21.1 yaw
        double cos = Mth.sin(boat.getYRot() * Mth.DEG_TO_RAD);
        double sin = -Mth.cos(boat.getYRot() * Mth.DEG_TO_RAD);
        double dy = boat.getY() - boat.yo;
        RandomSource random = boat.getRandom();
        Level level = boat.level();
        for (int i = 0; i < 1.0 + speed * 60.0; i++) {
            double along = random.nextFloat() * 2.0F - 1.0F;
            double side = (random.nextInt(2) * 2 - 1) * 0.7;
            double x;
            double z;
            if (random.nextBoolean()) {
                x = boat.getX() - cos * along * 0.8 + sin * side;
                z = boat.getZ() - sin * along * 0.8 - cos * side;
            } else {
                x = boat.getX() + cos + sin * along * 0.7;
                z = boat.getZ() + sin - cos * along * 0.7;
            }
            level.addParticle(ParticleTypes.SPLASH, x, boat.getY() - 0.125, z, dx, dy, dz);
        }
    }
}

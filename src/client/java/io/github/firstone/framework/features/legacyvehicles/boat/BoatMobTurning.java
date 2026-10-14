package io.github.firstone.framework.features.legacyvehicles.boat;

import io.github.firstone.framework.features.legacyvehicles.LegacyVehiclesFeature;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.Boat;

import java.util.function.Supplier;

/**
 * Mobs in boats turn their head and body as when they stand on the ground (only on this client)
 *
 * <p>Each tick 1.21.1 {@code Boat.positionRider} locks a mob's body to the boat's front and sets its head to its yaw,
 * kept within 105 degrees of the front ({@code clampRotation}). The server does the same before it sends the head, so
 * where the head looks is the server's rule; this client keeps the head exactly as received, like for a mob on the
 * ground. Setting it again on the client fought with the received head: with "Legacy Entity Sync" (Animatium) the
 * head is set at once while the yaw is smoothed, so the head jumped ahead and back every tick and shook.</p>
 *
 * <p>The body is not sent by the server, each client works it out: this client lets the mob's own body turning
 * ({@code BodyRotationControl}, or the 1.7.10 rules of Animatium) follow the head, and never lets the head get further
 * from the body than the mob's neck allows ({@code getMaxHeadYRot}, mostly 75 degrees), so the head never looks
 * backwards. The seat and the yaw stay as the boat sets them.</p>
 *
 * <p>Those body rules turn a moving mob's body at once and a standing mob's body only after a short wait. A mob in a
 * boat does not walk, but it seemed to move: the boat carries it, and with "Legacy Entity Sync" each update pulls it
 * for a moment towards the server's position, rounded to 1/32 block, before the boat puts it back in its seat. So
 * while its body turns, a carried mob counts as standing still ({@link #turnBodyStanding}) and waits exactly as it
 * does on the ground.</p>
 */
public final class BoatMobTurning {

    private BoatMobTurning() {}

    /**
     * Tells whether a passenger's body may turn on this client while the boat places it
     *
     * @param boat      the boat
     * @param passenger one of its passengers
     * @return true on the client, for a mob (not a player), when "Mobs Turn in Boats" is on
     */
    public static boolean applies(Boat boat, Entity passenger) {
        return boat.level().isClientSide() && LegacyVehiclesFeature.getConfig().mobsTurnInBoats
            && passenger instanceof Mob;
    }

    /**
     * Tells whether a mob is carried by a boat on this client, so its body turns as when standing
     *
     * @param mob a mob whose body is about to turn
     * @return true on the client, for a mob in a boat, when "Mobs Turn in Boats" is on
     */
    public static boolean isCarried(Mob mob) {
        return mob.getVehicle() instanceof Boat boat && applies(boat, mob);
    }

    /**
     * Turns a carried mob's body with its own rules as if it stood still: its last position counts as its position
     * while they run, then is put back (it is also where it is drawn from between two ticks)
     *
     * <p>Called from {@code Mob.tickHeadTurn} while {@link #isCarried} is true.</p>
     *
     * @param mob      the mob
     * @param turnBody the mob's body turning ({@code tickHeadTurn} itself)
     * @return what {@code tickHeadTurn} returns
     */
    public static float turnBodyStanding(Mob mob, Supplier<Float> turnBody) {
        double xo = mob.xo;
        double zo = mob.zo;
        mob.xo = mob.getX();
        mob.zo = mob.getZ();
        try {
            return turnBody.get();
        } finally {
            mob.xo = xo;
            mob.zo = zo;
        }
    }

    /**
     * Lets the boat place a mob as in 1.21.1, then gives back the head the server sent and the mob's own body, turned
     * as far as needed to keep the head within the mob's neck
     *
     * <p>Called from {@code Boat.positionRider} on the client while {@link #applies} is true.</p>
     *
     * @param mob   the mob
     * @param place the boat's placing ({@code positionRider} itself), which also sets the head and locks the body
     */
    public static void placeTurningBody(Mob mob, Runnable place) {
        float head = mob.getYHeadRot();
        float body = mob.yBodyRot;
        place.run();
        mob.setYHeadRot(head);
        mob.yBodyRot = Mth.rotateIfNecessary(body, head, mob.getMaxHeadYRot());
    }
}

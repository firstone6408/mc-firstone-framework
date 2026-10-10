package io.github.firstone.framework.devtest.features.legacyvehicles;

import io.github.firstone.framework.common.boat.LegacyBoatPhysics;
import io.github.firstone.framework.features.legacyvehicles.boat.LegacyBoatModel;
import net.minecraft.world.entity.vehicle.Boat;

/**
 * The only place where the Legacy Vehicles tests reach into the features' inner classes
 *
 * <p>The tests check behavior from outside (speeds, drops, sounds, screenshots) and only change the feature configs.
 * A few checks need an inner class directly; they go through here, so when a refactor moves or renames one of these
 * classes, only this file changes.</p>
 */
final class LegacyVehiclesInternals {

    private LegacyVehiclesInternals() {}

    /**
     * Tells whether a boat is drawn with the 1.7.10 model ({@code LegacyBoatModel.draws})
     *
     * @param boat the client's boat
     * @return true if the 1.7.10 model draws it
     */
    static boolean drawsLegacyModel(Boat boat) {
        return LegacyBoatModel.draws(boat);
    }

    /**
     * Pushes a boat and everyone standing on it apart once, with the 1.7.10 formula
     * ({@code LegacyBoatPhysics.pushStanders}); call on the server thread
     *
     * @param boat the server's boat
     */
    static void pushStanders(Boat boat) {
        LegacyBoatPhysics.pushStanders(boat, true, entity -> true);
    }
}

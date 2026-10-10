package io.github.firstone.framework.client.mixin.legacyvehicles;

import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the floating state of a boat, which the boat updates every tick on both sides ({@code Boat.getStatus})
 *
 * <p>Used by {@code LegacyBoatModel} to place the 1.7.10 model above the water.</p>
 */
@Mixin(Boat.class)
public interface BoatAccessor {

    /**
     * Returns where the boat is: in water, under water, on land or in the air
     *
     * @return the status of the last tick
     */
    @Accessor("status")
    Boat.Status legacyVehicles$getStatus();

    /**
     * Returns the height of the water's surface at the boat, while it is in water
     *
     * @return the surface height of the last tick
     */
    @Accessor("waterLevel")
    double legacyVehicles$getWaterLevel();
}

package io.github.firstone.framework.mixin.boat;

import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads the floating state of a boat, which the boat updates at the start of every tick on both sides
 * ({@code Boat.getStatus})
 *
 * <p>Used through {@code LegacyBoatPhysics} by Legacy Vehicles (client) and Legacy Mechanics (server).</p>
 */
@Mixin(Boat.class)
public interface BoatAccessor {

    /**
     * Returns where the boat is: in water, under water, on land or in the air
     *
     * @return the status of the last tick
     */
    @Accessor("status")
    Boat.Status framework$getStatus();

    /**
     * Returns the height of the water's surface at the boat, while it is in water
     *
     * @return the surface height of the last tick
     */
    @Accessor("waterLevel")
    double framework$getWaterLevel();

    /**
     * Returns the average friction of the blocks under the boat, while it is on land
     *
     * @return the friction of the last tick
     */
    @Accessor("landFriction")
    float framework$getLandFriction();
}

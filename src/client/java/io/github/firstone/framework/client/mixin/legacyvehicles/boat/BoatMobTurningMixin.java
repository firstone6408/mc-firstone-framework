package io.github.firstone.framework.client.mixin.legacyvehicles.boat;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.firstone.framework.features.legacyvehicles.boat.BoatMobTurning;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Lets mobs in boats turn their head and body as on the ground on this client ({@link BoatMobTurning})
 *
 * <p>Wraps {@code Boat.positionRider}, which each tick places a passenger and, among others, sets its head to its yaw
 * and locks its body to the boat's front. Only the client's copy of the boat is changed; the server is left as it
 * is.</p>
 */
@Mixin(Boat.class)
public class BoatMobTurningMixin {

    @WrapMethod(method = "positionRider")
    private void legacyVehicles$turnBody(Entity passenger, Entity.MoveFunction moveFunction,
                                         Operation<Void> original) {
        if (BoatMobTurning.applies((Boat) (Object) this, passenger) && passenger instanceof Mob mob) {
            BoatMobTurning.placeTurningBody(mob, () -> original.call(passenger, moveFunction));
        } else {
            original.call(passenger, moveFunction);
        }
    }
}

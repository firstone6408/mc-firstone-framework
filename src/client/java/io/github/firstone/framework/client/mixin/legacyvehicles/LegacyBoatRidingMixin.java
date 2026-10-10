package io.github.firstone.framework.client.mixin.legacyvehicles;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import io.github.firstone.framework.common.boat.LegacyBoatPhysics;
import io.github.firstone.framework.features.legacyvehicles.LegacyBoats;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 1.7.10 riding ({@link LegacyBoats#rides}): players look all around in any boat, and a lone player in a wooden boat
 * sits a little lower and behind the middle ({@link LegacyBoats#seat})
 *
 * <p>1.21.1 turns the passengers with the boat and keeps them within 105 degrees of its front
 * ({@code positionRider}, {@code onPassengerTurned} → {@code clampRotation}); 1.7.10 did neither. Two passengers,
 * chest boats and bamboo rafts keep the 1.21.1 seats.</p>
 */
@Mixin(Boat.class)
public abstract class LegacyBoatRidingMixin {

    @WrapMethod(method = "positionRider")
    private void legacyVehicles$freeLook(Entity passenger, Entity.MoveFunction moveFunction, Operation<Void> original) {
        if (!LegacyBoats.rides((Boat) (Object) this, passenger)) {
            original.call(passenger, moveFunction);
            return;
        }
        // let the boat place the passenger, then undo the turn and the clamp it applies
        float yRot = passenger.getYRot();
        float yRotO = passenger.yRotO;
        float headRot = passenger.getYHeadRot();
        float bodyRot = passenger instanceof LivingEntity living ? living.yBodyRot : 0.0F;
        original.call(passenger, moveFunction);
        passenger.setYRot(yRot);
        passenger.yRotO = yRotO;
        passenger.setYHeadRot(headRot);
        if (passenger instanceof LivingEntity living) {
            living.yBodyRot = bodyRot;
        }
    }

    @Inject(method = "onPassengerTurned", at = @At("HEAD"), cancellable = true)
    private void legacyVehicles$noClamp(Entity passenger, CallbackInfo ci) {
        if (LegacyBoats.rides((Boat) (Object) this, passenger)) {
            ci.cancel();
        }
    }

    @ModifyReturnValue(method = "getPassengerAttachmentPoint", at = @At("RETURN"))
    private Vec3 legacyVehicles$seat(Vec3 attachment, Entity passenger, EntityDimensions dimensions, float scale) {
        Boat boat = (Boat) (Object) this;
        return boat.getPassengers().size() == 1 && LegacyBoatPhysics.isWoodenBoat(boat)
            && LegacyBoats.rides(boat, passenger)
            ? LegacyBoats.seat(boat, passenger)
            : attachment;
    }
}

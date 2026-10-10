package io.github.firstone.framework.mixin.legacymechanics.boat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.common.boat.LegacyBoatPhysics;
import io.github.firstone.framework.features.legacymechanics.boat.LegacyBoatRules;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Server side of {@link LegacyBoatRules} for boats: keeps each boat's state, moves boats without a driver with the
 * 1.7.10 motion (with "Legacy Boat Coasting"), checks the moves the server makes and breaks a crashed boat at the
 * start of its tick
 *
 * <p>On the server, {@code floatBoat} and {@code move} only run for boats without a player driver (a driven boat is
 * moved by its driver's client). Everything here does nothing on the client.</p>
 */
@Mixin(Boat.class)
public class LegacyBoatRulesMixin implements LegacyBoatRules.Holder {

    /** Server state of this boat */
    @Unique
    private final LegacyBoatRules.State legacyMechanics$state = new LegacyBoatRules.State();

    @Override
    public LegacyBoatRules.State legacyMechanics$boatState() {
        return this.legacyMechanics$state;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void legacyMechanics$tick(CallbackInfo ci) {
        Boat boat = (Boat) (Object) this;
        if (!boat.level().isClientSide()) {
            LegacyBoatRules.tick(boat);
        }
    }

    @Inject(method = "floatBoat", at = @At("HEAD"), cancellable = true)
    private void legacyMechanics$coastBeforeMove(CallbackInfo ci) {
        Boat boat = (Boat) (Object) this;
        if (!boat.level().isClientSide() && LegacyBoatRules.coasting()) {
            // no driver input; on slippery blocks the boat slides like in 1.21.1
            LegacyBoatRules.handling(boat).beforeMove(boat, 0, 0, LegacyBoatPhysics.slideFriction(boat));
            ci.cancel();
        }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/vehicle/Boat;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
    private void legacyMechanics$afterServerMove(Boat boat, MoverType type, Vec3 move, Operation<Void> original) {
        original.call(boat, type, move);
        if (!boat.level().isClientSide()) {
            if (LegacyBoatRules.coasting()) {
                LegacyBoatRules.handling(boat).afterMove(boat);
            }
            LegacyBoatRules.afterServerMove(boat, move);
        }
    }
}

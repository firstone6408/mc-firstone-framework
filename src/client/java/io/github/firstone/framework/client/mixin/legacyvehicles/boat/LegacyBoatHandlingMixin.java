package io.github.firstone.framework.client.mixin.legacyvehicles.boat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.common.boat.LegacyBoatPhysics;
import io.github.firstone.framework.features.legacyvehicles.boat.LegacyBoats;
import io.github.firstone.framework.features.legacyvehicles.LegacyVehiclesFeature;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drives the local player's boat with the 1.7.10 handling ({@link LegacyBoatPhysics.Handling}), with 1.21.1 sliding on
 * ice when "Ice Boating" is on, and adds the 1.7.10 splash around fast boats
 *
 * <p>{@code Boat.tick} moves a boat on the side that controls it: {@code floatBoat}, then {@code controlBoat} (the
 * client only), then {@code move}. With the 1.7.10 handling, {@code floatBoat} runs the first half of the 1.7.10 tick
 * instead, {@code controlBoat} only stops the paddles (1.7.10 boats had none, so no paddle sound or animation), and
 * the second half runs after {@code move}.</p>
 *
 * <p>A boat without a driver and without passengers is moved by the client itself with the 1.7.10 motion, as in
 * 1.7.10 ({@link LegacyBoats#simulates}): in {@code tick}, the boat is moved where 1.21.1 would set its motion to
 * zero, and {@code tickLerp} (following the server's positions) is skipped, unless the server's boat is more than a
 * block away ({@code lerpTo}); then the client follows the server for 3 ticks.</p>
 *
 * <p>Boats are pushed as in 1.7.10: the boat the local player drives by whoever stands on it and by the boats and
 * entities touching it (in {@code floatBoat}), a boat moved by the client by whoever stands on it (in its own move),
 * and on a boat that follows the server only the local player standing on it is pushed back (at the end of
 * {@code tick}).</p>
 */
@Mixin(Boat.class)
public abstract class LegacyBoatHandlingMixin {

    @Shadow
    private boolean inputLeft;

    @Shadow
    private boolean inputRight;

    @Shadow
    private boolean inputUp;

    @Shadow
    private boolean inputDown;

    @Shadow
    private float deltaRotation;

    @Shadow
    private int lerpSteps;

    @Shadow
    public abstract void setPaddleState(boolean left, boolean right);

    /** 1.7.10 motion state of this boat */
    @Unique
    private final LegacyBoatPhysics.Handling legacyVehicles$handling = new LegacyBoatPhysics.Handling();

    /** Ticks left during which the client follows the server's position again instead of moving the boat itself */
    @Unique
    private int legacyVehicles$resyncTicks;

    /** The client moves this boat by itself during this tick ({@link LegacyBoats#simulates}) */
    @Unique
    private boolean legacyVehicles$simulating;

    @Inject(method = "tick", at = @At("HEAD"))
    private void legacyVehicles$startTick(CallbackInfo ci) {
        Boat boat = (Boat) (Object) this;
        if (!boat.level().isClientSide()) {
            return;
        }
        if (this.legacyVehicles$resyncTicks > 0) {
            this.legacyVehicles$resyncTicks--;
        }
        this.legacyVehicles$simulating = this.legacyVehicles$resyncTicks == 0 && LegacyBoats.simulates(boat);
    }

    @Inject(method = "tickLerp", at = @At("HEAD"), cancellable = true)
    private void legacyVehicles$skipServerPositions(CallbackInfo ci) {
        if (this.legacyVehicles$simulating) {
            ci.cancel();
        }
    }

    @Inject(method = "lerpTo", at = @At("TAIL"))
    private void legacyVehicles$resync(double x, double y, double z, float yRot, float xRot, int steps,
                                       CallbackInfo ci) {
        Boat boat = (Boat) (Object) this;
        if (LegacyBoats.simulates(boat) && LegacyBoats.needsResync(boat, new Vec3(x, y, z))) {
            // 1.7.10 moved a boat it moved itself to the server's position in 3 steps when it was a block away
            this.lerpSteps = 3;
            this.legacyVehicles$resyncTicks = 3;
        }
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/vehicle/Boat;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void legacyVehicles$simulate(Boat boat, Vec3 motion, Operation<Void> original) {
        if (this.legacyVehicles$simulating) {
            // where 1.21.1 stops a boat it does not move, move it with the 1.7.10 motion, without a driver
            LegacyBoats.simulate(boat, this.legacyVehicles$handling, LegacyBoats.slideFriction(boat));
        } else {
            original.call(boat, motion);
        }
    }

    @Inject(method = "floatBoat", at = @At("HEAD"), cancellable = true)
    private void legacyVehicles$beforeMove(CallbackInfo ci) {
        Boat boat = (Boat) (Object) this;
        if (LegacyBoats.handles(boat)) {
            LegacyBoats.pushDriven(boat);
            this.legacyVehicles$handling.beforeMove(boat, (this.inputUp ? 1 : 0) - (this.inputDown ? 1 : 0),
                (this.inputLeft ? 1 : 0) - (this.inputRight ? 1 : 0),
                LegacyBoats.slideFriction(boat));
            ci.cancel();
        }
    }

    @Inject(method = "controlBoat", at = @At("HEAD"), cancellable = true)
    private void legacyVehicles$noPaddles(CallbackInfo ci) {
        if (LegacyBoats.handles((Boat) (Object) this)) {
            // the boat no longer turns by itself; a turn left from the 1.21.1 handling would also turn the rider
            this.deltaRotation = 0.0F;
            setPaddleState(false, false);
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
        target = "Lnet/minecraft/world/entity/vehicle/Boat;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"))
    private void legacyVehicles$afterMove(CallbackInfo ci) {
        Boat boat = (Boat) (Object) this;
        if (LegacyBoats.handles(boat)) {
            this.legacyVehicles$handling.afterMove(boat);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void legacyVehicles$endTick(CallbackInfo ci) {
        Boat boat = (Boat) (Object) this;
        if (!boat.level().isClientSide()) {
            return;
        }
        if (!this.legacyVehicles$simulating && !boat.isControlledByLocalInstance() && LegacyBoats.handles(boat)) {
            LegacyBoats.pushLocalStander(boat);
        }
        if (LegacyVehiclesFeature.getConfig().boatSplash) {
            LegacyBoats.splash(boat);
        }
    }
}

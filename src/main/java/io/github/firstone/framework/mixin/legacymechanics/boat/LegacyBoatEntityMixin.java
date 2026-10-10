package io.github.firstone.framework.mixin.legacymechanics.boat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.legacymechanics.boat.LegacyBoatRules;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Boat rules of the server that live in {@code Entity}: pushes between boats and other entities, and the splash of
 * a bobbing boat
 *
 * <ul>
 *   <li><b>Push</b> — {@code Entity.push(Entity)} pushes both entities apart, but skips each one that carries
 *       passengers ({@code isVehicle}); for boats that check is left out ({@link LegacyBoatRules#pushesOccupied}).
 *       The push of a boat a player drives is sent to that player's client instead
 *       ({@link LegacyBoatRules#sendPushToDriver}).</li>
 *   <li><b>Splash</b> — the water splash sound of a floating boat bobbing back into the water is left out
 *       ({@link LegacyBoatRules#skipsSplash}); the client leaves out its particles ({@code LegacyBoatSplashMixin} of
 *       Legacy Vehicles).</li>
 * </ul>
 */
@Mixin(Entity.class)
public class LegacyBoatEntityMixin {

    @WrapOperation(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;isVehicle()Z"))
    private boolean legacyMechanics$pushOccupiedBoats(Entity entity, Operation<Boolean> original) {
        return original.call(entity) && !LegacyBoatRules.pushesOccupied(entity);
    }

    @WrapOperation(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;push(DDD)V"))
    private void legacyMechanics$pushOrSend(Entity entity, double x, double y, double z, Operation<Void> original) {
        if (!LegacyBoatRules.sendPushToDriver(entity, x, z)) {
            original.call(entity, x, y, z);
        }
    }

    @Inject(method = "doWaterSplashEffect", at = @At("HEAD"), cancellable = true)
    private void legacyMechanics$noBobbingSplash(CallbackInfo ci) {
        if (LegacyBoatRules.skipsSplash((Entity) (Object) this)) {
            ci.cancel();
        }
    }
}

package io.github.firstone.framework.mixin.legacymechanics;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.firstone.framework.features.legacymechanics.LegacyBoatRules;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets the server push boats that carry passengers when they touch other entities
 * ({@link LegacyBoatRules#pushesOccupied})
 *
 * <p>{@code Entity.push(Entity)} pushes both entities apart, but skips each one that carries passengers
 * ({@code isVehicle}); for boats that check is left out. The push of a boat a player drives is sent to that player's
 * client instead ({@link LegacyBoatRules#sendPushToDriver}).</p>
 */
@Mixin(Entity.class)
public class LegacyBoatPushMixin {

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
}
